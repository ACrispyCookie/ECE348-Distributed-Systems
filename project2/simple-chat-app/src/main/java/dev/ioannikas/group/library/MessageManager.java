package dev.ioannikas.group.library;

import dev.ioannikas.group.api.SerializableObject;
import dev.ioannikas.group.gms.serializable.ClientInfo;
import dev.ioannikas.group.library.catoc.CatocManager;
import dev.ioannikas.group.library.serializable.PacketUDP;
import dev.ioannikas.group.gms.serializable.replies.GMSUpdateReply;
import dev.ioannikas.group.library.serializable.requests.GroupDataRequest;
import dev.ioannikas.group.library.serializable.requests.NormalRequest;

import java.util.*;
import java.util.concurrent.*;

public class MessageManager {

    private final ConcurrentMap<Integer, Queue<NormalRequest>> receivedPackets;
    private final Map<List<Integer>, Queue<CompletableFuture<NormalRequest>>> waitingRequests;
    private final CatocManager catocManager;

    public MessageManager() {
        this.receivedPackets = new ConcurrentHashMap<>();
        this.waitingRequests = new HashMap<>();
        this.catocManager = new CatocManager();
    }

    public int send(int groupId, SerializableObject data) {
        GroupDataRequest packet = new GroupDataRequest(groupId, data, 0);
        catocManager.send(packet);
        return 0;
    }

    public NormalRequest receive(List<Integer> receivingList) throws InterruptedException, ExecutionException {
        NormalRequest matched = requestMatches(receivingList);
        if (matched != null)
            return matched;

        CompletableFuture<NormalRequest> future = addRequest(receivingList);
        return future.get();
    }

    public synchronized void handleReceivedPacket(PacketUDP reply) {
        if (reply instanceof NormalRequest normal && normal.getData() instanceof GMSUpdateReply update) {
            CompletableFuture<NormalRequest> waiting = requestWaiting(normal.getGroupId());
            catocManager.updateGroup(update);
            if (waiting != null) {
                waiting.complete(normal);
            } else
                receivedPackets.computeIfAbsent(normal.getGroupId(), k -> new LinkedBlockingQueue<>()).add(normal);
        } else if (reply instanceof NormalRequest normal) {
            List<NormalRequest> ready = catocManager.processIncoming(normal); // Will get them from CATOC manager
            for (NormalRequest req : ready) {
                CompletableFuture<NormalRequest> waiting = requestWaiting(normal.getGroupId());
                if (waiting != null) {
                    waiting.complete(req);
                } else
                    receivedPackets.computeIfAbsent(normal.getGroupId(), k -> new LinkedBlockingQueue<>()).add(normal);
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

    private synchronized CompletableFuture<NormalRequest> requestWaiting(int groupId) {
        List<Integer> selected = null;

        for (List<Integer> waiting : waitingRequests.keySet()) {
            if (waiting.contains(groupId) && !waitingRequests.get(waiting).isEmpty()) {
                selected = waiting;
                break;
            }
        }

        return selected != null ? waitingRequests.get(selected).remove() : null;
    }

    private synchronized NormalRequest requestMatches(List<Integer> groups) {
        for (int group : groups) {
            Queue<NormalRequest> queue = receivedPackets.get(group);
            if (queue == null)
                continue;

            if (!queue.isEmpty())
                return queue.remove();
        }

        return null;
    }

    private synchronized CompletableFuture<NormalRequest> addRequest(List<Integer> groups) {
        waitingRequests.computeIfAbsent(groups, i -> new LinkedList<>());

        CompletableFuture<NormalRequest> future = new CompletableFuture<>();
        waitingRequests.get(groups).offer(future);
        return future;
    }
}
