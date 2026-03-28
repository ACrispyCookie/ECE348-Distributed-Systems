package dev.ioannikas.group.library;

import dev.ioannikas.group.GroupAPI;
import dev.ioannikas.group.api.GroupMessagePair;
import dev.ioannikas.group.api.SerializableObject;
import dev.ioannikas.group.gms.serializable.ClientInfo;
import dev.ioannikas.group.library.catoc.CatocManager;
import dev.ioannikas.group.library.serializable.PacketUDP;
import dev.ioannikas.group.gms.serializable.replies.GMSUpdateReply;
import dev.ioannikas.group.library.serializable.requests.NormalRequest;

import java.util.*;
import java.util.concurrent.*;

public class MessageManager {

    private final ConcurrentMap<Integer, Queue<GroupMessagePair>> receivedPackets;
    private final Map<List<Integer>, Queue<CompletableFuture<GroupMessagePair>>> waitingRequests;
    private final GroupAPI api;
    private final CatocManager catocManager;

    public MessageManager(GroupAPI api, boolean enableTrm) {
        this.api = api;
        this.receivedPackets = new ConcurrentHashMap<>();
        this.waitingRequests = new HashMap<>();
        this.catocManager = new CatocManager(api, enableTrm);
    }

    public void send(int groupId, String username, SerializableObject data) {
        catocManager.send(groupId, username, data);
    }

    public GroupMessagePair receive(List<Integer> receivingList) throws InterruptedException, ExecutionException {
        CompletableFuture<GroupMessagePair> future;

        synchronized (this) {
            GroupMessagePair matched = requestMatches(receivingList);
            if (matched != null)
                return matched;

            future = addRequest(receivingList);
        }

        return future.get();
    }

    public synchronized void handleReceivedPacket(PacketUDP reply) {
        if (reply instanceof NormalRequest normal && normal.getData() instanceof GMSUpdateReply update) {
            List<GroupMessagePair> completed = catocManager.updateGroup(update);
            if (!completed.isEmpty())
                receivedPackets.computeIfAbsent(normal.getGroupId(), k -> new LinkedBlockingQueue<>()).addAll(completed);

            CompletableFuture<GroupMessagePair> waiting = requestWaiting(normal.getGroupId());
            if (waiting != null) {
                waiting.complete(new GroupMessagePair(normal.getGroupId(), update));
            } else
                receivedPackets.computeIfAbsent(normal.getGroupId(), k -> new LinkedBlockingQueue<>()).add(new GroupMessagePair(normal.getGroupId(), update));
        } else if (reply instanceof NormalRequest normal) {
            List<GroupMessagePair> ready = catocManager.receive(api.getUsername(normal.getGroupId()), normal);
            for (GroupMessagePair req : ready) {
                CompletableFuture<GroupMessagePair> waiting = requestWaiting(normal.getGroupId());
                if (waiting != null) {
                    waiting.complete(req);
                } else
                    receivedPackets.computeIfAbsent(normal.getGroupId(), k -> new LinkedBlockingQueue<>()).add(req);
            }
        }
    }

    public void addGroup(int groupId, List<ClientInfo> clients) {
        catocManager.addGroup(groupId, clients);
    }

    public void removeGroup(int groupId) {
        catocManager.removeGroup(groupId);
    }

    public void shutdown() {
        catocManager.shutdown();
    }

    private CompletableFuture<GroupMessagePair> requestWaiting(int groupId) {
        List<Integer> selected = null;

        for (List<Integer> waiting : waitingRequests.keySet()) {
            if (waiting.contains(groupId) && !waitingRequests.get(waiting).isEmpty()) {
                selected = waiting;
                break;
            }
        }

        return selected != null ? waitingRequests.get(selected).remove() : null;
    }

    private GroupMessagePair requestMatches(List<Integer> groups) {
        for (int group : groups) {
            Queue<GroupMessagePair> queue = receivedPackets.get(group);
            if (queue == null)
                continue;

            if (!queue.isEmpty())
                return queue.remove();
        }

        return null;
    }

    private synchronized CompletableFuture<GroupMessagePair> addRequest(List<Integer> groups) {
        waitingRequests.computeIfAbsent(groups, i -> new LinkedList<>());

        CompletableFuture<GroupMessagePair> future = new CompletableFuture<>();
        waitingRequests.get(groups).offer(future);
        return future;
    }
}
