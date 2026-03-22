package dev.ioannikas.gms.network;

import dev.ioannikas.gms.managers.GroupManager;
import dev.ioannikas.gms.utility.Printer;
import dev.ioannikas.grouplib.network.serializable.ClientInfo;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSReply;
import dev.ioannikas.grouplib.network.serializable.gms.replies.UpdateGMSReply;
import dev.ioannikas.grouplib.network.serializable.gms.requests.RequestGMSPacket;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

public class ReceiverThread extends Thread {

    private static int TIMEOUT = 5;
    private static boolean shuttingDown = false;

    private final GroupManager manager;
    private final Socket socket;
    private final Map<Integer, String> groups;

    public ReceiverThread(GroupManager manager, Socket socket) {
        this.manager = manager;
        this.socket = socket;
        this.groups = new HashMap<>();
        setName("client-" + socket.getInetAddress().getHostAddress());
    }

    public void run() {
        Printer.printDebug("Client " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort() + " connected!");

        try {
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            socket.setSoTimeout(TIMEOUT * 1000);

            while (!shuttingDown) {
                RequestGMSPacket packet = (RequestGMSPacket) in.readObject();
                switch (packet.getType()) {
                    case JOIN -> handleJoin(packet);
                    case LEAVE -> handleLeave(packet);
                    default -> {
                        Printer.printDebug("Received heartbeat from " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort() + "!");
                    }
                }
            }
        } catch (SocketTimeoutException e) {
            handleFailure();
        } catch (IOException | ClassNotFoundException e) {
            Printer.printErr("Client " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort() + " encountered error: " + e.getMessage());
            e.printStackTrace();
            handleFailure();
        }
    }

    private void handleJoin(RequestGMSPacket packet) throws IOException {
        ClientInfo newClient = new ClientInfo(packet.getUsername(), packet.getIpAddress(), packet.getPort());
        Printer.printDebug("Received join from " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort() + "!" + "\n" + newClient);

        int groupId = manager.findOrCreateGroup(packet.getGroupName());
        GroupManager.ReturnCode code = manager.addToGroup(groupId, newClient);
        if (code != GroupManager.ReturnCode.SUCCESS)
            Printer.printErr("Error while adding member " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort() + " [" + packet.getUsername() + "] to group");

        groups.put(groupId, packet.getUsername());

        // Send reply packet to source
        new UpdateGMSReply(GMSReply.ReplyType.JOIN, GMSReply.ReplyResult.SUCCESS, groupId, UpdateGMSReply.FailReason.USERNAME_TAKEN, manager.getClients(groupId)).send(socket);
        // Send update packet to group
    }

    private void handleLeave(RequestGMSPacket packet) {
        int groupId = packet.getGroupId();
        String username = packet.getUsername();
        Printer.printDebug("Received join from " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort() + "!" + "\n" + "Group ID: " + groupId + "\nUsername:" + username);

        GroupManager.ReturnCode code = manager.removeFromGroup(groupId, username);
        if (code != GroupManager.ReturnCode.SUCCESS)
            Printer.printErr("Error while removing member " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort() + " [" + username + "] to group");

        groups.remove(groupId);

        // Send reply packet to source
        // Send update packet to group
    }

    private void handleFailure() {
        Printer.printDebug("Client " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort() + " has disconnected!");
        for (int groupId : groups.keySet()) {
            String username = groups.get(groupId);

            GroupManager.ReturnCode code = manager.removeFromGroup(groupId, username);
            if (code != GroupManager.ReturnCode.SUCCESS)
                Printer.printErr("Error while removing member " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort() + " [" + username + "] to group");

            // Send update packet to group
        }

        groups.clear();
    }

    public static void setTimeout(int seconds) {
        TIMEOUT = seconds;
    }

    public static void onShutdown() {
        shuttingDown = true;
    }
}
