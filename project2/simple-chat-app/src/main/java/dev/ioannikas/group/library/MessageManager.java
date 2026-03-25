package dev.ioannikas.group.library;

import dev.ioannikas.group.library.serializable.PacketUDP;
import dev.ioannikas.group.gms.serializable.replies.GMSUpdateReply;
import dev.ioannikas.group.library.serializable.requests.NormalRequest;

import java.util.*;
import java.util.concurrent.*;

public class MessageManager {

    private final ConcurrentMap<Integer, Queue<NormalRequest>> groupPackets;
    private final Map<List<Integer>, Queue<CompletableFuture<NormalRequest>>> requests;

    public MessageManager() {
        this.groupPackets = new ConcurrentHashMap<>();
        this.requests = new HashMap<>();
    }

    public int send(int groupId, Object data) {
        NormalRequest packet = new NormalRequest(groupId, data);

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
        if (reply instanceof NormalRequest normal && normal.getData() instanceof GMSUpdateReply) {
            CompletableFuture<NormalRequest> waiting = requestWaiting(normal.getGroupId());
            if (waiting != null) {
                waiting.complete(normal);
            } else
                groupPackets.computeIfAbsent(reply.getGroupId(), k -> new LinkedBlockingQueue<>()).add(normal);
            return;
        } else {

        }
    }

    private synchronized CompletableFuture<NormalRequest> requestWaiting(int groupId) {
        List<Integer> selected = null;

        for (List<Integer> waiting : requests.keySet()) {
            if (waiting.contains(groupId) && !requests.get(waiting).isEmpty())
                selected = waiting;
        }

        return selected != null ? requests.get(selected).remove() : null;
    }

    private synchronized NormalRequest requestMatches(List<Integer> groups) {
        for (int group : groups) {
            Queue<NormalRequest> queue = groupPackets.get(group);
            if (queue == null)
                continue;

            if (!queue.isEmpty())
                return queue.remove();
        }

        return null;
    }

    private synchronized CompletableFuture<NormalRequest> addRequest(List<Integer> groups) {
        requests.computeIfAbsent(groups, i -> new LinkedList<>());

        CompletableFuture<NormalRequest> future = new CompletableFuture<>();
        requests.get(groups).offer(future);
        return future;
    }
}
