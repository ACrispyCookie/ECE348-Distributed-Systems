package dev.ioannikas.group.library;


import dev.ioannikas.chatapp.utility.Printer;
import dev.ioannikas.group.GroupAPI;
import dev.ioannikas.group.gms.serializable.replies.GMSJoinReply;
import dev.ioannikas.group.gms.serializable.replies.GMSLeaveReply;
import dev.ioannikas.group.gms.serializable.replies.GMSReply;
import dev.ioannikas.group.gms.serializable.requests.GMSJoinRequest;
import dev.ioannikas.group.gms.serializable.requests.GMSLeaveRequest;
import dev.ioannikas.group.gms.serializable.requests.GMSRequest;
import dev.ioannikas.group.library.utility.Group;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class GMSManager {

    private final int HEARTBEAT_INTERVAL = 2;
    private final Thread heartbeatSender;
    private final Map<Integer, CompletableFuture<GMSReply>> pendingFutures;
    private final AtomicInteger nextFutureId = new AtomicInteger(1);

    public GMSManager() {
        this.pendingFutures = new ConcurrentHashMap<>();
        this.heartbeatSender = new Thread(this::sender, "heartbeat-sender");
        heartbeatSender.start();
    }

    public GMSJoinReply join(String groupName, String username, String ipAddress, int port) {
        CompletableFuture<GMSReply> future = new CompletableFuture<>();

        try {
            GMSJoinRequest packet = new GMSJoinRequest(username, nextFutureId.getAndIncrement(), groupName, ipAddress, port);
            pendingFutures.put(packet.getFutureId(), future);
            GroupAPI.getNetworkManager().sendGms(packet);

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
            GroupAPI.getNetworkManager().sendGms(packet);

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
            GroupAPI.getMessageManager().addGroup(join.getGroupId(), join.getClients());
            futureId = join.getFutureId();
        } else if (reply instanceof GMSLeaveReply leave) {
            GroupAPI.getMessageManager().removeGroup(leave.getGroupId());
            futureId = leave.getFutureId();
        } else {
            return;
        }

        CompletableFuture<GMSReply> future = pendingFutures.remove(futureId);
        // Should never happen
        if (future == null) {
            System.err.println("The bad thing happened");
            return;
        }
        future.complete(reply);
    }

    private void sender() {
        while(!GroupAPI.isShuttingDown()) {
            try {
                Thread.sleep(HEARTBEAT_INTERVAL * 1000);
                GroupAPI.getNetworkManager().sendGms(GMSRequest.simpleHeartbeat());
            } catch (InterruptedException ignored) {

            } catch (IOException e) {
                Printer.printErr("[GMSSender] Error communicating with the GMS.");
                System.exit(1);
            }
        }
    }

    public void shutdown() {
        heartbeatSender.interrupt();
    }

}
