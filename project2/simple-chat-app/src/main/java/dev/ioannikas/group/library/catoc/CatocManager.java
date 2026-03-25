package dev.ioannikas.group.library.catoc;

import dev.ioannikas.group.gms.serializable.ClientInfo;
import dev.ioannikas.group.gms.serializable.replies.GMSUpdateReply;
import dev.ioannikas.group.library.serializable.requests.NormalRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class CatocManager {

    private final Map<Integer, List<ClientInfo>> groups;
    private final Thread retransmitter;

    public CatocManager() {
        groups = new ConcurrentHashMap<>();
        retransmitter = new Thread(this::retransmitter, "retransmitter");
    }

    private void retransmitter() {
//        while (!GroupAPI.isShuttingDown()) {
//
//        }
    }

    public void send(NormalRequest request) {

    }

    public List<NormalRequest> processIncoming(NormalRequest request) {
        System.out.println("received");
        return null;
    }

    public void shutdown() {
        retransmitter.interrupt();
    }

    public void addGroup(int groupId, List<ClientInfo> clients) {
        groups.putIfAbsent(groupId, new ArrayList<>(clients));
    }

    public void removeGroup(int groupId) {
        groups.remove(groupId);
    }

    public void updateGroup(GMSUpdateReply reply) {
        if (reply.getClientAction() == GMSUpdateReply.Action.JOINED) {
            groups.compute(reply.getGroupId(), (k, list) -> {
                if (list == null) return null;
                list.add(reply.getClient());
                return list;
            });
        } else {
            groups.compute(reply.getGroupId(), (k, list) -> {
                if (list == null) return null;
                list.remove(reply.getClient());
                return list.isEmpty() ? null : list;
            });
        }
    }
}
