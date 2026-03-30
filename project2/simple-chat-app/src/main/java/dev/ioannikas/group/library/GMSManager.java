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
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class GMSManager {

    private final int HEARTBEAT_INTERVAL = 2;
    private final Thread heartbeatSender;
    private final GroupAPI api;
    private final Map<Integer, FutureUsernamePair> pendingFutures;
    private final AtomicInteger nextFutureId = new AtomicInteger(1);

    public GMSManager(GroupAPI api) {
        this.api = api;
        this.pendingFutures = new ConcurrentHashMap<>();
        this.heartbeatSender = new Thread(this::sender, "heartbeat-sender");
        heartbeatSender.start();
    }

    public GMSJoinReply join(String groupName, String username, String ipAddress, int port) {
        CompletableFuture<GMSReply> future = new CompletableFuture<>();

        try {
            Printer.printDebug(api.getNetworkManager().getPort() + " [CatocManager] Sending join request for " + groupName + " with " + username + " and " + ipAddress);
            GMSJoinRequest packet = new GMSJoinRequest(username, nextFutureId.getAndIncrement(), groupName, ipAddress, port);
            pendingFutures.put(packet.getFutureId(), new FutureUsernamePair(future, username));
            api.getNetworkManager().sendGms(packet);

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
            Printer.printDebug(api.getNetworkManager().getPort() + " [CatocManager] Sending leave request for " + groupId + " with " + username);
            GMSLeaveRequest packet = new GMSLeaveRequest(username, nextFutureId.getAndIncrement(), groupId);
            pendingFutures.put(packet.getFutureId(), new FutureUsernamePair(future, username));
            api.getNetworkManager().sendGms(packet);

            return (GMSLeaveReply) future.get();
        } catch (InterruptedException | ExecutionException | IOException e) {
            Printer.printErr("[GMSManager] Error completing a leave request.");
            e.printStackTrace();
        }

        return null;
    }

    public void handleReceivedPacket(GMSReply reply) {
        FutureUsernamePair future;

        if (reply instanceof GMSJoinReply join) {
            Printer.printDebug(api.getNetworkManager().getPort() + " [CatocManager] Received join reply for " + join.getGroupId() + " with " + join.getClients() + " and " + join.getReason());
            future = pendingFutures.get(join.getFutureId());
            if (join.getReason() == GMSJoinReply.FailReason.NONE)
                api.getMessageManager().addGroup(join.getGroupId(), future.getUsername(), join.getClients());
        } else if (reply instanceof GMSLeaveReply leave) {
            Printer.printDebug(api.getNetworkManager().getPort() + " [CatocManager] Received leave reply for " + leave.getGroupId() + " with " + leave.getReason());
            future = pendingFutures.get(leave.getFutureId());
            if (leave.getReason() == GMSLeaveReply.FailReason.NONE)
                api.getMessageManager().removeGroup(leave.getGroupId());
        } else {
            return;
        }

        // Should never happen
        if (future == null)
            return;
        future.getFuture().complete(reply);
    }

    private void sender() {
        while(!api.isShuttingDown()) {
            try {
                Thread.sleep(HEARTBEAT_INTERVAL * 1000);
                api.getNetworkManager().sendGms(GMSRequest.simpleHeartbeat());
            } catch (InterruptedException ignored) {

            } catch (IOException e) {
                Printer.printErr("[GMSSender] Error communicating with the GMS.");
                System.exit(1);
            }
        }
    }

    public void shutdown() throws InterruptedException {
        heartbeatSender.interrupt();
        heartbeatSender.join();
    }

    private static class FutureUsernamePair {
        private final CompletableFuture<GMSReply> future;
        private final String username;

        public FutureUsernamePair(CompletableFuture<GMSReply> future, String username) {
            this.future = future;
            this.username = username;
        }

        public CompletableFuture<GMSReply> getFuture() {
            return future;
        }

        public String getUsername() {
            return username;
        }
    }

}
