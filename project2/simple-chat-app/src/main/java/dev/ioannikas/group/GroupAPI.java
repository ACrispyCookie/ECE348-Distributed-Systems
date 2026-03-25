package dev.ioannikas.group;

import dev.ioannikas.group.api.JoinReply;
import dev.ioannikas.group.api.LeaveReply;
import dev.ioannikas.group.api.Pair;
import dev.ioannikas.group.library.GMSManager;
import dev.ioannikas.group.library.MessageManager;
import dev.ioannikas.group.library.NetworkManager;
import dev.ioannikas.group.gms.serializable.replies.GMSJoinReply;
import dev.ioannikas.group.gms.serializable.replies.GMSLeaveReply;
import dev.ioannikas.group.gms.serializable.replies.GMSUpdateReply;
import dev.ioannikas.group.library.serializable.requests.NormalRequest;

import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class GroupAPI {

    private static NetworkManager networkManager;
    private static MessageManager messageManager;
    private static GMSManager gmsManager;
    private static boolean shuttingDown;

    private static Map<Integer, String> groups;

    public static void init(String gmsAddress, int gmsPort, int appPort) throws IOException {
        shuttingDown = false;
        groups = new ConcurrentHashMap<>();
        networkManager = new NetworkManager(gmsAddress, gmsPort, appPort);
        gmsManager = new GMSManager();
        messageManager = new MessageManager();
    }

    public static JoinReply join(String groupName, String username) {
        GMSJoinReply reply = gmsManager.join(groupName, username, networkManager.getIpAddress(), networkManager.getPort());
        if (reply.getReason() == GMSJoinReply.FailReason.NONE)
            groups.put(reply.getGroupId(), username);

        return reply.toApiVersion();
    }

    public static LeaveReply leave(int groupId) {
        if (!groups.containsKey(groupId))
            return null;

        GMSLeaveReply reply = gmsManager.leave(groupId, groups.get(groupId));
        if (reply.getReason() == GMSLeaveReply.FailReason.NONE)
            groups.remove(groupId);

        return reply.toApiVersion();
    }

    public static <T extends Serializable> int send(int groupId, T message) {
        if (!groups.containsKey(groupId))
            return -1;

        return messageManager.send(groupId, message);
    }

    public static Pair<Integer, Object> receive(List<Integer> groups) throws InterruptedException, ExecutionException {
        NormalRequest reply = messageManager.receive(groups);
        if (reply.getData() instanceof GMSUpdateReply update)
            return new Pair<>(reply.getGroupId(), update.toApiVersion());
        else
            return new Pair<>(reply.getGroupId(), reply.getData());
    }

    public static NetworkManager getNetworkManager() {
        return networkManager;
    }

    public static MessageManager getMessageManager() {
        return messageManager;
    }

    public static GMSManager getGmsManager() {
        return gmsManager;
    }

    public static void shutdown() {
        shuttingDown = true;
        for (Map.Entry<Integer, String> entry : groups.entrySet()) {
            gmsManager.leave(entry.getKey(), entry.getValue());
        }

        gmsManager.shutdown();
        networkManager.shutdown();
    }

    public static boolean isShuttingDown() {
        return shuttingDown;
    }
}
