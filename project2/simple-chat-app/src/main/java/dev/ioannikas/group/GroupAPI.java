package dev.ioannikas.group;

import dev.ioannikas.group.api.JoinReply;
import dev.ioannikas.group.api.LeaveReply;
import dev.ioannikas.group.library.GMSManager;
import dev.ioannikas.group.library.MessagingManager;
import dev.ioannikas.group.library.NetworkManager;
import dev.ioannikas.group.gms.serializable.replies.GMSJoinReply;
import dev.ioannikas.group.gms.serializable.replies.GMSLeaveReply;
import dev.ioannikas.group.gms.serializable.replies.GMSUpdateReply;
import dev.ioannikas.group.library.serializable.requests.NormalRequest;

import java.io.IOException;
import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GroupAPI {

    private NetworkManager networkManager;
    private MessagingManager messagingManager;
    private GMSManager gmsManager;

    private Map<Integer, String> groups;

    public void init(String gmsAddress, int gmsPort, int appPort) throws IOException {
        this.groups = new ConcurrentHashMap<>();
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

        NormalRequest reply = messagingManager.receive(groupId, block);
        if (reply.getData() instanceof GMSUpdateReply update)
            return update.toApiVersion();
        else
            return reply.getData();
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
