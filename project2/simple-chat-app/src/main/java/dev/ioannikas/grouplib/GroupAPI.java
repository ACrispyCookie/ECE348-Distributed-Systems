package dev.ioannikas.grouplib;

import dev.ioannikas.grouplib.api.JoinReply;
import dev.ioannikas.grouplib.api.LeaveReply;
import dev.ioannikas.grouplib.network.serializable.PacketUDP;
import dev.ioannikas.grouplib.managers.GMSManager;
import dev.ioannikas.grouplib.managers.MessagingManager;
import dev.ioannikas.grouplib.managers.NetworkManager;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSJoinReply;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSLeaveReply;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSUpdateReply;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class GroupAPI {

    private NetworkManager networkManager;
    private MessagingManager messagingManager;
    private GMSManager gmsManager;

    private Map<Integer, String> groups;

    public void init(String gmsAddress, int gmsPort, int appPort) throws IOException {
        this.groups = new HashMap<>();
        this.networkManager = new NetworkManager(this, gmsAddress, gmsPort, appPort);
        this.gmsManager = new GMSManager(this);
        this.messagingManager = new MessagingManager(this);
    }

    public JoinReply join(String groupName, String username) {
        GMSJoinReply reply = gmsManager.join(groupName, username, networkManager.getIpAddress(), networkManager.getPort());
        if (reply.getReason() == GMSJoinReply.FailReason.NONE)
            groups.put(reply.getGroupId(), username);

        return reply.toApiVersion();
    }

    public LeaveReply leave(int groupId) {
        if (!groups.containsKey(groupId))
            return null;

        GMSLeaveReply reply = gmsManager.leave(groupId, groups.get(groupId));
        if (reply.getReason() == GMSLeaveReply.FailReason.NONE)
            groups.remove(groupId);

        return reply.toApiVersion();
    }

    public <T extends Serializable> int send(int groupId, T message) {
        if (!groups.containsKey(groupId))
            return -1;

        return messagingManager.send(groupId, message);
    }

    public Object receive(int groupId, boolean block) throws InterruptedException {
        if (!groups.containsKey(groupId))
            return null;

        PacketUDP reply = messagingManager.receive(groupId, block);
        if (reply.getObject() instanceof GMSUpdateReply update)
            return update.toApiVersion();
        else
            return reply.getObject();
    }

    public NetworkManager getNetworkManager() {
        return networkManager;
    }

    public MessagingManager getMessagingManager() {
        return messagingManager;
    }

    public GMSManager getGmsManager() {
        return gmsManager;
    }

    public void shutdown() {
        gmsManager.shutdown();
        messagingManager.shutdown();
        networkManager.shutdown();
    }
}
