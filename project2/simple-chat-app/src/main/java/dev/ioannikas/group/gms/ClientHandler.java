package dev.ioannikas.group.gms;

import dev.ioannikas.group.gms.utility.GroupManager;
import dev.ioannikas.group.gms.utility.Printer;
import dev.ioannikas.group.gms.serializable.ClientInfo;
import dev.ioannikas.group.gms.serializable.PacketTCP;
import dev.ioannikas.group.gms.serializable.replies.GMSJoinReply;
import dev.ioannikas.group.gms.serializable.replies.GMSLeaveReply;
import dev.ioannikas.group.gms.serializable.replies.GMSReply;
import dev.ioannikas.group.gms.serializable.replies.GMSUpdateReply;
import dev.ioannikas.group.gms.serializable.requests.GMSJoinRequest;
import dev.ioannikas.group.gms.serializable.requests.GMSLeaveRequest;
import dev.ioannikas.group.gms.serializable.requests.GMSRequest;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ClientHandler {

    private static final int TIMEOUT = 5;

    private final SocketChannel channel;
    private final GroupManager manager;
    private final Map<Integer, String> groups;
    private long failsAt;

    public ClientHandler(SocketChannel channel, GroupManager manager) throws IOException {
        this.channel = channel;
        this.manager = manager;
        this.groups = new HashMap<>();
        this.failsAt = System.currentTimeMillis() + TIMEOUT * 1000L;
        Printer.printDebug("Client " + channel.getRemoteAddress() + " connected!");
    }

    public void handleRequest(ByteBuffer buffer) throws IOException, ClassNotFoundException {
        GMSRequest request = (GMSRequest) PacketTCP.deserialize(buffer);

        switch (Objects.requireNonNull(request).getType()) {
            case JOIN -> handleJoin((GMSJoinRequest) request);
            case LEAVE -> handleLeave((GMSLeaveRequest) request);
            case HEARTBEAT -> {
                failsAt = System.currentTimeMillis() + (TIMEOUT * 1000L);
                Printer.printDebug("Received heartbeat from " + channel.getRemoteAddress() + "!");
            }
        }
    }

    public boolean checkFailure() throws IOException {
        if (System.currentTimeMillis() > failsAt) {
            handleFailure();
            return true;
        }
        return false;
    }

    public void handleFailure() throws IOException {
        Printer.printInfo("Client " + channel.getRemoteAddress() + " has disconnected!");
        for (int groupId : groups.keySet()) {
            String username = groups.get(groupId);

            GroupManager.ReturnCode code = manager.removeFromGroup(groupId, username);
            if (code != GroupManager.ReturnCode.SUCCESS)
                Printer.printErr("Error while removing member " + channel.getRemoteAddress() + " [" + username + "] to group " + groupId + " (" + code + ")");

            sendUpdatePacket(groupId, username, GMSUpdateReply.Action.LEFT);
        }

        groups.clear();
        channel.close();
    }

    private void handleJoin(GMSJoinRequest packet) throws IOException {
        ClientInfo newClient = new ClientInfo(packet.getUsername(), packet.getIpAddress(), packet.getPort(), channel.getRemoteAddress());
        Printer.printInfo("Received join from " + newClient + " Group: " + packet.getGroupName());

        int groupId = manager.findOrCreateGroup(packet.getGroupName());
        GroupManager.ReturnCode code = manager.addToGroup(groupId, newClient);
        if (code == GroupManager.ReturnCode.CLIENT_ALREADY_EXISTS) {
            GMSJoinReply reply = new GMSJoinReply(groupId, packet.getFutureId(), GMSJoinReply.FailReason.USERNAME_TAKEN, null);
            sendReply(reply);
            return;
        } else if (code != GroupManager.ReturnCode.SUCCESS) {
            Printer.printErr("Error while adding member " + channel.getRemoteAddress() + " [" + packet.getUsername() + "] to group " + groupId + " (" + code + ")");
            GMSJoinReply reply = new GMSJoinReply(groupId, packet.getFutureId(), GMSJoinReply.FailReason.UNKNOWN, null);
            sendReply(reply);
            return;
        }

        groups.put(groupId, packet.getUsername());
        GMSJoinReply reply = new GMSJoinReply(groupId, packet.getFutureId(), GMSJoinReply.FailReason.NONE, manager.getClients(groupId));
        sendReply(reply);
        sendUpdatePacket(groupId, packet.getUsername(), GMSUpdateReply.Action.JOINED);
    }

    private void handleLeave(GMSLeaveRequest packet) throws IOException {
        int groupId = packet.getGroupId();
        String username = packet.getUsername();
        Printer.printInfo("Received leave from " + channel.getRemoteAddress() + "!" + " Username: " + username + " Group: " + groupId);

        GroupManager.ReturnCode code = manager.removeFromGroup(groupId, username);
        if (code != GroupManager.ReturnCode.SUCCESS) {
            Printer.printErr("Error while removing member " + channel.getRemoteAddress() + " [" + packet.getUsername() + "] from group " + groupId + " (" + code + ")");
            GMSLeaveReply reply = new GMSLeaveReply(groupId, packet.getFutureId(), GMSLeaveReply.FailReason.UNKNOWN);
            sendReply(reply);
            return;
        }

        groups.remove(groupId);
        GMSLeaveReply reply = new GMSLeaveReply(groupId, packet.getFutureId(), GMSLeaveReply.FailReason.NONE);
        sendReply(reply);
        sendUpdatePacket(groupId, username, GMSUpdateReply.Action.LEFT);
    }

    private void sendUpdatePacket(int groupId, String username, GMSUpdateReply.Action action) {
        GMSUpdateReply update = new GMSUpdateReply(groupId, username, action);
        manager.forEachClient(groupId, (c) -> {
            SocketAddress address = c.getAddress();

            try {
                if (address == channel.getRemoteAddress())
                    return;
                ClientHandler clientHandler = GMS.getReceiver(address);
                clientHandler.sendReply(update);
            } catch (IOException e) {
                Printer.printErr("Error sending update packet to " + address);
            }
        });
    }

    private void sendReply(GMSReply reply) throws IOException {
        ByteBuffer buffer = PacketTCP.serialize(reply);

        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    public static int getTIMEOUT() {
        return TIMEOUT;
    }
}
