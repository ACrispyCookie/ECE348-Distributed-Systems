package dev.ioannikas.group.library;


import dev.ioannikas.chatapp.utility.Printer;
import dev.ioannikas.group.GroupAPI;
import dev.ioannikas.group.gms.serializable.replies.GMSJoinReply;
import dev.ioannikas.group.gms.serializable.replies.GMSLeaveReply;
import dev.ioannikas.group.gms.serializable.replies.GMSReply;
import dev.ioannikas.group.gms.serializable.requests.GMSJoinRequest;
import dev.ioannikas.group.gms.serializable.requests.GMSLeaveRequest;
import dev.ioannikas.group.gms.serializable.requests.GMSRequest;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class GMSManager extends GeneralManager {

    private final int HEARTBEAT_INTERVAL = 2;
    private final Map<Integer, CompletableFuture<GMSReply>> pendingFutures;
    private final AtomicInteger nextFutureId = new AtomicInteger(1);

    public GMSManager(GroupAPI api) {
        super(api);
        this.pendingFutures = new ConcurrentHashMap<>();

        start();
    }

    public GMSJoinReply join(String groupName, String username, String ipAddress, int port) {
        CompletableFuture<GMSReply> future = new CompletableFuture<>();

        try {
            GMSJoinRequest packet = new GMSJoinRequest(username, nextFutureId.getAndIncrement(), groupName, ipAddress, port);
            pendingFutures.put(packet.getFutureId(), future);
            getAPI().getNetworkManager().sendGms(packet);

            return (GMSJoinReply) future.get();
        } catch (InterruptedException | ExecutionException | IOException e) {
            Printer.printErr("[GMSManager] Error completing a join request.");
            e.printStackTrace();
        }

        return null;
    }

    public GMSLeaveReply leave(int groupId, String username) {
        CompletableFuture<GMSReply> future = new CompletableFuture<>();

        try {
            GMSLeaveRequest packet = new GMSLeaveRequest(username, nextFutureId.getAndIncrement(), groupId);
            pendingFutures.put(packet.getFutureId(), future);
            getAPI().getNetworkManager().sendGms(packet);

            return (GMSLeaveReply) future.get();
        } catch (InterruptedException | ExecutionException | IOException e) {
            Printer.printErr("[GMSManager] Error completing a leave request.");
            e.printStackTrace();
        }

        return null;
    }

    public void handleReceivedPacket(GMSReply reply) {
        int futureId;

        if (reply instanceof GMSJoinReply join) {
            futureId = join.getFutureId();
        } else if (reply instanceof GMSLeaveReply leave) {
            futureId = leave.getFutureId();
        } else {
            return;
        }

        CompletableFuture<GMSReply> future = pendingFutures.remove(futureId);
        // Should never happen
        if (future == null)
            return;
        future.complete(reply);
    }

    private void sender() {
        while(!isShuttingDown()) {
            try {
                Thread.sleep(HEARTBEAT_INTERVAL * 1000);
                getAPI().getNetworkManager().sendGms(GMSRequest.simpleHeartbeat());
            } catch (InterruptedException ignored) {

            } catch (IOException e) {
                Printer.printErr("[GMSSender] Error communicating with the GMS.");
            }
        }
    }

    @Override
    protected List<Thread> getThreads() {
        Thread sender = new Thread(this::sender);
        sender.setName("gms-sender");

        return List.of(sender);
    }

    @Override
    protected void onShutdown() {

    }

}
