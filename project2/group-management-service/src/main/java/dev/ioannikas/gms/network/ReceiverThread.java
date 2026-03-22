package dev.ioannikas.gms.network;

import dev.ioannikas.gms.managers.GroupManager;
import dev.ioannikas.gms.utility.Printer;
import dev.ioannikas.grouplib.network.serializable.ClientInfo;
import dev.ioannikas.grouplib.network.serializable.Connection;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSJoinReply;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSLeaveReply;
import dev.ioannikas.grouplib.network.serializable.gms.requests.GMSJoinRequest;
import dev.ioannikas.grouplib.network.serializable.gms.requests.GMSLeaveRequest;
import dev.ioannikas.grouplib.network.serializable.gms.requests.GMSRequest;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

public class ReceiverThread extends Thread {

    private static int TIMEOUT = 5;
    private static boolean shuttingDown = false;

    private final GroupManager manager;
    private final Connection connection;
    private final Map<Integer, String> groups;

    public ReceiverThread(GroupManager manager, Socket socket) {
        this.manager = manager;
        this.connection = new Connection(socket);
        this.groups = new HashMap<>();
        setName("client-" + socket.getInetAddress().getHostAddress());
    }

    public void run() {
        try {
            connection.init();
            Printer.printDebug("Client " + connection.getIp() + ":" + connection.getPort() + " connected!");
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
            Printer.printErr("Client " + connection.getIp() + ":" + connection.getPort() + " encountered error: " + e.getMessage());
            e.printStackTrace();
            handleFailure();
        }
    }

    private void handleJoin(GMSJoinRequest packet) throws IOException {
        ClientInfo newClient = new ClientInfo(packet.getUsername(), packet.getIpAddress(), packet.getPort());
        Printer.printInfo("Received join from " + connection.getIp() + ":" + connection.getPort() + "!" + "\n" + newClient + "\nGroup: " + packet.getGroupName());

        int groupId = manager.findOrCreateGroup(packet.getGroupName());
        GroupManager.ReturnCode code = manager.addToGroup(groupId, newClient);
        if (code == GroupManager.ReturnCode.CLIENT_ALREADY_EXISTS) {
            GMSJoinReply reply = new GMSJoinReply(groupId, GMSJoinReply.FailReason.USERNAME_TAKEN, null);
            connection.sendPacket(reply);
            return;
        } else if (code != GroupManager.ReturnCode.SUCCESS) {
            Printer.printErr("Error while adding member " + connection.getIp() + ":" + connection.getPort() + " [" + packet.getUsername() + "] to group");
            GMSJoinReply reply = new GMSJoinReply(groupId, GMSJoinReply.FailReason.UNKNOWN, null);
            connection.sendPacket(reply);
            return;
        }

        groups.put(groupId, packet.getUsername());
        GMSJoinReply reply = new GMSJoinReply(groupId, GMSJoinReply.FailReason.NONE, manager.getClients(groupId));
        connection.sendPacket(reply);
        // TODO Send update packet to group
    }

    private void handleLeave(GMSLeaveRequest packet) throws IOException {
        int groupId = packet.getGroupId();
        String username = packet.getUsername();
        Printer.printInfo("Received leave from " + connection.getIp() + ":" + connection.getPort() + "!" + "\nUsername: " + username + "\nGroup: " + groupId);

        GroupManager.ReturnCode code = manager.removeFromGroup(groupId, username);
        if (code != GroupManager.ReturnCode.SUCCESS) {
            Printer.printErr("Error while adding member " + connection.getIp() + ":" + connection.getPort() + " [" + packet.getUsername() + "] to group");
            GMSLeaveReply reply = new GMSLeaveReply(groupId, GMSLeaveReply.FailReason.UNKNOWN);
            connection.sendPacket(reply);
            return;
        }

        groups.remove(groupId);
        GMSLeaveReply reply = new GMSLeaveReply(groupId, GMSLeaveReply.FailReason.NONE);
        connection.sendPacket(reply);
        // TODO Send update packet to group
    }

    private void handleFailure() {
        Printer.printDebug("Client " + connection.getIp() + ":" + connection.getPort() + " has disconnected!");
        for (int groupId : groups.keySet()) {
            String username = groups.get(groupId);

            GroupManager.ReturnCode code = manager.removeFromGroup(groupId, username);
            if (code != GroupManager.ReturnCode.SUCCESS)
                Printer.printErr("Error while removing member " + connection.getIp() + ":" + connection.getPort() + " [" + username + "] to group");

            // TODO Send update packet to group
        }

        groups.clear();
        try {
            connection.getSocket().close();
        } catch (IOException e) {
            Printer.printErr("Error while closing socket of " + connection.getIp() + ":" + connection.getPort());
        }
    }

    public static void setTimeout(int seconds) {
        TIMEOUT = seconds;
    }

    public static void onShutdown() {
        shuttingDown = true;
    }
}
