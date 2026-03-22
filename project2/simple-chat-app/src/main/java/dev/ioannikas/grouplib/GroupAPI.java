package dev.ioannikas.grouplib;

import dev.ioannikas.grouplib.api.JoinLeaveResult;
import dev.ioannikas.grouplib.managers.GMSManager;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSReply;
import dev.ioannikas.grouplib.network.serializable.gms.replies.UpdateGMSReply;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class GroupAPI {

    private Map<Integer, String> groups;
    private GMSManager gmsManager;
    private String ipAddress;
    private int port;

    private boolean shuttingDown = false;

    public void init(String gmsAddress, int gmsPort) {
        this.groups = new HashMap<>();
        this.gmsManager = new GMSManager(gmsAddress, gmsPort);
    }

    public JoinLeaveResult join(String groupName, String username) {
        try {
            UpdateGMSReply reply = gmsManager.join(groupName, username, ipAddress, port);
            if (reply.getResult() == GMSReply.ReplyResult.ERROR)
                return reply.toApiVersion();

            groups.put(reply.getGroupId(), username);
            return reply.toApiVersion();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public JoinLeaveResult leave(int groupId) {
        try {
            if (!groups.containsKey(groupId))
                return null;

            UpdateGMSReply reply = gmsManager.leave(groupId, groups.get(groupId));
            if (reply.getResult() == GMSReply.ReplyResult.ERROR)
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
}
