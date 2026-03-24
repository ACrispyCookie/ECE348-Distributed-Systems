package dev.ioannikas.group.gms;

import dev.ioannikas.group.gms.utility.Printer;
import dev.ioannikas.group.gms.serializable.ClientInfo;
import dev.ioannikas.group.gms.serializable.ConnectionTCP;
import dev.ioannikas.group.gms.serializable.replies.GMSJoinReply;
import dev.ioannikas.group.gms.serializable.replies.GMSLeaveReply;
import dev.ioannikas.group.gms.serializable.replies.GMSUpdateReply;
import dev.ioannikas.group.gms.serializable.requests.GMSJoinRequest;
import dev.ioannikas.group.gms.serializable.requests.GMSLeaveRequest;
import dev.ioannikas.group.gms.serializable.requests.GMSRequest;

import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

public class ClientThread extends Thread {

    private static final int TIMEOUT = 5;
    private static boolean shuttingDown = false;

    private final GroupManager manager;
    private final ConnectionTCP connection;
    private final Map<Integer, String> groups;

    public ClientThread(GroupManager manager, Socket socket) throws IOException {
        this.manager = manager;
        this.connection = new ConnectionTCP(socket);
        this.groups = new HashMap<>();
        setName("client-" + socket.getInetAddress().getHostAddress());
    }

    public void run() {
        try {
            Printer.printInfo("Client " + connection.getIp() + ":" + connection.getPort() + " connected!");
            connection.getSocket().setSoTimeout(TIMEOUT * 1000);

            while (!shuttingDown) {
                GMSRequest packet = (GMSRequest) connection.nextObject();
                switch (packet.getType()) {
                    case JOIN -> handleJoin((GMSJoinRequest) packet);
                    case LEAVE -> handleLeave((GMSLeaveRequest) packet);
                    default -> Printer.printDebug("Received heartbeat from " + connection.getIp() + ":" + connection.getPort() + "!");
                }
            }
        } catch (SocketTimeoutException e) {
            handleFailure();
        } catch (IOException | ClassNotFoundException e) {
            if (!(e instanceof EOFException)) {
                Printer.printErr("Client " + connection.getIp() + ":" + connection.getPort() + " encountered error: " + e.getMessage());
                e.printStackTrace();
            }
            handleFailure();
        }
    }

    private void handleJoin(GMSJoinRequest packet) throws IOException {
        ClientInfo newClient = new ClientInfo(packet.getUsername(), packet.getIpAddress(), packet.getPort(), this);
        Printer.printInfo("Received join from " + newClient + " Group: " + packet.getGroupName());

        int groupId = manager.findOrCreateGroup(packet.getGroupName());
        GroupManager.ReturnCode code = manager.addToGroup(groupId, newClient);
        if (code == GroupManager.ReturnCode.CLIENT_ALREADY_EXISTS) {
            GMSJoinReply reply = new GMSJoinReply(groupId, packet.getFutureId(), GMSJoinReply.FailReason.USERNAME_TAKEN, null);
            connection.sendPacket(reply);
            return;
        } else if (code != GroupManager.ReturnCode.SUCCESS) {
            Printer.printErr("Error while adding member " + connection.getIp() + ":" + connection.getPort() + " [" + packet.getUsername() + "] to group " + groupId + " (" + code + ")");
            GMSJoinReply reply = new GMSJoinReply(groupId, packet.getFutureId(), GMSJoinReply.FailReason.UNKNOWN, null);
            connection.sendPacket(reply);
            return;
        }

        groups.put(groupId, packet.getUsername());
        GMSJoinReply reply = new GMSJoinReply(groupId, packet.getFutureId(), GMSJoinReply.FailReason.NONE, manager.getClients(groupId));
        connection.sendPacket(reply);

        sendUpdatePacket(groupId, packet.getUsername(), GMSUpdateReply.Action.JOINED);
    }

    private void handleLeave(GMSLeaveRequest packet) throws IOException {
        int groupId = packet.getGroupId();
        String username = packet.getUsername();
        Printer.printInfo("Received leave from " + connection.getIp() + ":" + connection.getPort() + "!" + " Username: " + username + " Group: " + groupId);

        GroupManager.ReturnCode code = manager.removeFromGroup(groupId, username);
        if (code != GroupManager.ReturnCode.SUCCESS) {
            Printer.printErr("Error while removing member " + connection.getIp() + ":" + connection.getPort() + " [" + packet.getUsername() + "] from group " + groupId + " (" + code + ")");
            GMSLeaveReply reply = new GMSLeaveReply(groupId, packet.getFutureId(), GMSLeaveReply.FailReason.UNKNOWN);
            connection.sendPacket(reply);
            return;
        }

        groups.remove(groupId);
        GMSLeaveReply reply = new GMSLeaveReply(groupId, packet.getFutureId(), GMSLeaveReply.FailReason.NONE);
        connection.sendPacket(reply);

        sendUpdatePacket(groupId, username, GMSUpdateReply.Action.LEFT);
    }

    private void handleFailure() {
        Printer.printInfo("Client " + connection.getIp() + ":" + connection.getPort() + " has disconnected!");
        for (int groupId : groups.keySet()) {
            String username = groups.get(groupId);

            GroupManager.ReturnCode code = manager.removeFromGroup(groupId, username);
            if (code != GroupManager.ReturnCode.SUCCESS)
                Printer.printErr("Error while removing member " + connection.getIp() + ":" + connection.getPort() + " [" + username + "] to group " + groupId + " (" + code + ")");

            sendUpdatePacket(groupId, username, GMSUpdateReply.Action.LEFT);
        }

        groups.clear();
        try {
            connection.getSocket().close();
        } catch (IOException e) {
            Printer.printErr("Error while closing socket of " + connection.getIp() + ":" + connection.getPort());
        }
    }

    private void sendUpdatePacket(int groupId, String username, GMSUpdateReply.Action action) {
        GMSUpdateReply update = new GMSUpdateReply(groupId, username, action);
        manager.forEachClient(groupId, (c) -> {
            ClientThread thread = c.getManager();
            if (thread == this)
                return;

            ConnectionTCP tConnection = thread.connection;
            try {
                tConnection.sendPacket(update);
            } catch (IOException e) {
                Printer.printErr("Error sending update packet to " + tConnection.getIp() + ":" + tConnection.getPort());
            }
        });
    }

    public static void onShutdown() {
        shuttingDown = true;
    }
}
