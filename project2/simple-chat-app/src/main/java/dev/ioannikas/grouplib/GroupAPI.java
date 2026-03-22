package dev.ioannikas.grouplib;

import dev.ioannikas.chatapp.utility.Printer;
import dev.ioannikas.grouplib.api.JoinReply;
import dev.ioannikas.grouplib.api.LeaveReply;
import dev.ioannikas.grouplib.managers.GMSManager;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSJoinReply;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSLeaveReply;

import java.io.Serializable;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class GroupAPI {

    private Map<Integer, String> groups;
    private GMSManager gmsManager;
    private String ipAddress;
    private int port;

    private boolean shuttingDown = false;

    public boolean init(String gmsAddress, int gmsPort, int appPort) {
        this.groups = new HashMap<>();
        this.gmsManager = new GMSManager(gmsAddress, gmsPort);
        this.port = appPort;
        this.ipAddress = findLocalIp();
        Printer.printDebug("[GroupAPI] Using IP: " + ipAddress);

        return !gmsManager.isShuttingDown();
    }

    public JoinReply join(String groupName, String username) {
        try {
            GMSJoinReply reply = gmsManager.join(groupName, username, ipAddress, port);
            if (reply.getReason() != GMSJoinReply.FailReason.NONE)
                return reply.toApiVersion();

            groups.put(reply.getGroupId(), username);
            return reply.toApiVersion();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public LeaveReply leave(int groupId) {
        try {
            if (!groups.containsKey(groupId))
                return null;

            GMSLeaveReply reply = gmsManager.leave(groupId, groups.get(groupId));
            if (reply.getReason() == GMSLeaveReply.FailReason.UNKNOWN)
                return reply.toApiVersion();

            groups.remove(groupId);
            return reply.toApiVersion();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public <T extends Serializable> int send(int groupId, T message) {

        return 0;
    }

    public <T extends Serializable> T receive(int groupId, int block) {

        return null;
    }

    public void shutdown() {
        shuttingDown = true;
        gmsManager.shutdown();
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    private String findLocalIp() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);

            String ip = socket.getLocalAddress().getHostAddress();
            if (ip != null && !ip.equals("0.0.0.0") && !ip.startsWith("127.")) {
                return ip;
            }
        } catch (SocketException | UnknownHostException ignored) {

        }

        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            Printer.printErr("[GroupAPI] CRITICAL: Could not determine local IP address!");
            return "127.0.0.1";
        }
    }
}
