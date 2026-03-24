package dev.ioannikas.group.library;

import dev.ioannikas.group.GroupAPI;
import dev.ioannikas.group.api.Pair;
import dev.ioannikas.group.library.serializable.PacketUDP;
import dev.ioannikas.group.gms.serializable.replies.GMSUpdateReply;
import dev.ioannikas.group.library.serializable.requests.NormalRequest;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;

public class MessagingManager extends GeneralManager {

    private final ConcurrentMap<Integer, Queue<NormalRequest>> groupPackets;
    private final Queue<Pair<List<Integer>, CompletableFuture<NormalRequest>>> requests;

    public MessagingManager(GroupAPI api) {
        super(api);
        this.groupPackets = new ConcurrentHashMap<>();
        this.requests = new LinkedList<>();

        start();
    }

    public int send(int groupId, Object data) {
        NormalRequest packet = new NormalRequest(groupId, data);

        return 0;
    }

    public synchronized NormalRequest receive(List<Integer> receivingList) throws InterruptedException, ExecutionException {
        CompletableFuture<NormalRequest> future = new CompletableFuture<>();
        requests.offer(new Pair<>(receivingList, future));

        return future.get();
    }

    public synchronized void handleReceivedPacket(PacketUDP reply) {
        if (reply instanceof NormalRequest normal && normal.getData() instanceof GMSUpdateReply) {
            CompletableFuture<NormalRequest> future = requestMatches(reply);
            if (future != null) future.complete(normal);
            groupPackets.computeIfAbsent(reply.getGroupId(), k -> new LinkedBlockingQueue<>()).add(normal);
            return;
        } else {

        }
    }

    private CompletableFuture<NormalRequest> requestMatches(PacketUDP reply) {
        for (Pair<List<Integer>, CompletableFuture<NormalRequest>> request : requests) {
            if (request.getKey().contains(reply.getGroupId()))
                return request.getValue();
        }

        return null;
    }

    @Override
    protected List<Thread> getThreads() {
        return List.of();
    }

    @Override
    protected void onShutdown() {

    }
}
