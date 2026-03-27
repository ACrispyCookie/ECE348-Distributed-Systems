package dev.ioannikas.group;

import dev.ioannikas.group.api.GroupMessagePair;
import dev.ioannikas.group.api.JoinReply;
import dev.ioannikas.group.api.LeaveReply;
import dev.ioannikas.group.api.SerializableObject;
import dev.ioannikas.group.library.GMSManager;
import dev.ioannikas.group.library.MessageManager;
import dev.ioannikas.group.library.NetworkManager;
import dev.ioannikas.group.gms.serializable.replies.GMSJoinReply;
import dev.ioannikas.group.gms.serializable.replies.GMSLeaveReply;
import dev.ioannikas.group.gms.serializable.replies.GMSUpdateReply;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

public class GroupAPI {

    private final NetworkManager networkManager;
    private final MessageManager messageManager;
    private final GMSManager gmsManager;
    private boolean shuttingDown;
    private final Function<ByteBuffer, SerializableObject> deserializer;

    private final Map<Integer, String> groups;

    public GroupAPI(String gmsAddress, int gmsPort, int appPort, Function<ByteBuffer, SerializableObject> deserializer) throws IOException {
        shuttingDown = false;
        groups = new ConcurrentHashMap<>();
        networkManager = new NetworkManager(this, gmsAddress, gmsPort, appPort);
        gmsManager = new GMSManager(this);
        messageManager = new MessageManager(this);
        this.deserializer = deserializer;
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

    public <T extends SerializableObject> int send(int groupId, T message) {
        if (!groups.containsKey(groupId))
            return -1;

        messageManager.send(groupId, groups.get(groupId), message);
        return 0;
    }

    public GroupMessagePair receive(List<Integer> groups) throws InterruptedException, ExecutionException {
        GroupMessagePair reply = messageManager.receive(groups);
        if (reply.getMessage() instanceof GMSUpdateReply update)
            return new GroupMessagePair(reply.getGroupId(), update.toApiVersion());
        else
            return new GroupMessagePair(reply.getGroupId(), reply.getMessage());
    }

    public NetworkManager getNetworkManager() {
        return networkManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public GMSManager getGmsManager() {
        return gmsManager;
    }

    public String getUsername(int groupId) {
        return groups.get(groupId);
    }

    public Function<ByteBuffer, SerializableObject> getDeserializer() {
        return deserializer;
    }

    public void shutdown() {
        shuttingDown = true;
        messageManager.shutdown();
        gmsManager.shutdown();
        networkManager.shutdown();
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }
}
