package dev.ioannikas.gms.singlethread;

import dev.ioannikas.gms.managers.GroupManager;
import dev.ioannikas.gms.utility.Printer;
import dev.ioannikas.grouplib.network.serializable.gms.ClientInfo;
import dev.ioannikas.grouplib.network.serializable.gms.PacketTCP;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSJoinReply;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSLeaveReply;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSReply;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSUpdateReply;
import dev.ioannikas.grouplib.network.serializable.gms.requests.GMSJoinRequest;
import dev.ioannikas.grouplib.network.serializable.gms.requests.GMSLeaveRequest;
import dev.ioannikas.grouplib.network.serializable.gms.requests.GMSRequest;

import java.io.*;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Client {

    private static final int TIMEOUT = 5;

    private final SocketChannel channel;
    private final GroupManager manager;
    private final Map<Integer, String> groups;
    private long failsAt;

    public Client(SocketChannel channel, GroupManager manager) {
        this.channel = channel;
        this.manager = manager;
        this.groups = new HashMap<>();
        this.failsAt = System.currentTimeMillis() + TIMEOUT * 5000L;
    }

    public void handleRequest(byte[] array, int bytesRead) throws IOException, ClassNotFoundException {
        GMSRequest request = (GMSRequest) PacketTCP.deserialize(array, bytesRead);

        switch (Objects.requireNonNull(request).getType()) {
            case JOIN -> handleJoin((GMSJoinRequest) request);
            case LEAVE -> handleLeave((GMSLeaveRequest) request);
            case HEARTBEAT -> {
                failsAt += TIMEOUT * 1000L;
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

    private void handleJoin(GMSJoinRequest packet) throws IOException {
        ClientInfo newClient = new ClientInfo(packet.getUsername(), packet.getIpAddress(), packet.getPort(), channel.getRemoteAddress());
        Printer.printInfo("Received join from " + channel.getRemoteAddress() + "!" + "\n" + newClient + "\nGroup: " + packet.getGroupName());

        int groupId = manager.findOrCreateGroup(packet.getGroupName());
        GroupManager.ReturnCode code = manager.addToGroup(groupId, newClient);
        if (code == GroupManager.ReturnCode.CLIENT_ALREADY_EXISTS) {
            sendReply(new GMSJoinReply(groupId, packet.getFutureId(), GMSJoinReply.FailReason.USERNAME_TAKEN, null));
        } else if (code != GroupManager.ReturnCode.SUCCESS) {
            Printer.printErr("Error while adding member " + channel.getRemoteAddress() + " [" + packet.getUsername() + "] to group");
            sendReply(new GMSJoinReply(groupId, packet.getFutureId(), GMSJoinReply.FailReason.UNKNOWN, null));
        }

        groups.put(groupId, packet.getUsername());
        sendReply(new GMSJoinReply(groupId, packet.getFutureId(), GMSJoinReply.FailReason.NONE, manager.getClients(groupId)));

        GMSUpdateReply update = new GMSUpdateReply(groupId, newClient, GMSUpdateReply.Action.JOINED);
        manager.forEachClient(groupId, (c) -> {
            SocketAddress address = c.getManagerS();

            try {
                if (address == channel.getRemoteAddress())
                    return;
                Client client = SingleThreadGMS.getReceiver(address);
                client.sendReply(update);
            } catch (IOException e) {
                Printer.printErr("Error sending update packet to " + address);
            }
        });
    }

    private void handleLeave(GMSLeaveRequest packet) throws IOException {
        int groupId = packet.getGroupId();
        String username = packet.getUsername();
        Printer.printInfo("Received leave from " + channel.getRemoteAddress() + "!" + "\nUsername: " + username + "\nGroup: " + groupId);

        GroupManager.ReturnCode code = manager.removeFromGroup(groupId, username);
        if (code != GroupManager.ReturnCode.SUCCESS) {
            Printer.printErr("Error while adding member " + channel.getRemoteAddress() + " [" + packet.getUsername() + "] to group");
            sendReply(new GMSLeaveReply(groupId, packet.getFutureId(), GMSLeaveReply.FailReason.UNKNOWN));
        }

        groups.remove(groupId);
        sendReply(new GMSLeaveReply(groupId, packet.getFutureId(), GMSLeaveReply.FailReason.NONE));
        // TODO Send update packet to group
    }

    private void handleFailure() throws IOException {
        Printer.printDebug("Client " + channel.getRemoteAddress() + " has disconnected!");
        for (int groupId : groups.keySet()) {
            String username = groups.get(groupId);

            GroupManager.ReturnCode code = manager.removeFromGroup(groupId, username);
            if (code != GroupManager.ReturnCode.SUCCESS)
                Printer.printErr("Error while removing member " + channel.getRemoteAddress() + " [" + username + "] to group");

            // TODO Send update packet to group
        }

        groups.clear();
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
