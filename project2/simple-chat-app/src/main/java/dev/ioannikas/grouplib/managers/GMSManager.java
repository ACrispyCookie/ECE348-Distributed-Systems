package dev.ioannikas.grouplib.managers;


import dev.ioannikas.chatapp.utility.Printer;
import dev.ioannikas.grouplib.GroupAPI;
import dev.ioannikas.grouplib.network.serializable.gms.ConnectionTCP;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSJoinReply;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSLeaveReply;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSReply;
import dev.ioannikas.grouplib.network.serializable.gms.requests.GMSJoinRequest;
import dev.ioannikas.grouplib.network.serializable.gms.requests.GMSLeaveRequest;
import dev.ioannikas.grouplib.network.serializable.gms.requests.GMSRequest;

import java.io.IOException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;

public class GMSManager extends GeneralManager {

    private final int HEARTBEAT_INTERVAL = 2;
    private final ConnectionTCP connection;

    private final BlockingQueue<GMSReply> receiving;
    private final BlockingQueue<RequestTask> sending;
    private final Queue<CompletableFuture<GMSReply>> pendingFutures;

    public GMSManager(GroupAPI api) {
        super(api);
        this.connection = api.getNetworkManager().getGmsConnection();
        this.receiving = new LinkedBlockingQueue<>();
        this.sending = new LinkedBlockingQueue<>();
        this.pendingFutures = new ConcurrentLinkedQueue<>();

        start();
    }

    public GMSJoinReply join(String groupName, String username, String ipAddress, int port) {
        CompletableFuture<GMSReply> future = new CompletableFuture<>();

        try {
            GMSJoinRequest packet = new GMSJoinRequest(username, groupName, ipAddress, port);
            sending.put(new RequestTask(packet, future));

            return (GMSJoinReply) future.get();
        } catch (InterruptedException | ExecutionException e) {
            Printer.printErr("[GMS Manager] Error completing a join request.");
            e.printStackTrace();
        }

        return null;
    }

    public GMSLeaveReply leave(int groupId, String username) {
        CompletableFuture<GMSReply> future = new CompletableFuture<>();

        try {
            GMSLeaveRequest packet = new GMSLeaveRequest(username, groupId);
            sending.put(new RequestTask(packet, future));

            return (GMSLeaveReply) future.get();
        } catch (InterruptedException | ExecutionException e) {
            Printer.printErr("[GMS Manager] Error completing a leave request.");
            e.printStackTrace();
        }

        return null;
    }

    public void receiver() {
        while(!isShuttingDown()) {
            try {
                GMSReply reply = receiving.take();
                CompletableFuture<GMSReply> future = pendingFutures.poll();

                // Should never happen
                if (future == null)
                    continue;

                future.complete(reply);
            } catch (InterruptedException ignored) {

            }
        }
    }

    private void sender() {
        while(!isShuttingDown()) {
            try {
                RequestTask task = sending.poll(HEARTBEAT_INTERVAL, TimeUnit.SECONDS);

                if (task != null) {
                    pendingFutures.offer(task.future);
                    connection.sendPacket(task.packet);
                } else {
                    connection.sendPacket(GMSRequest.simpleHeartbeat());
                }
            } catch (InterruptedException ignored) {

            } catch (IOException e) {
                Printer.printErr("[GMS Sender] Error communicating with the GMS.");
            }
        }
    }

    public BlockingQueue<GMSReply> getReceiving() {
        return receiving;
    }

    @Override
    protected List<Thread> getThreads() {
        Thread sender = new Thread(this::sender);
        sender.setName("gms-sender");

        Thread receiver = new Thread(this::receiver);
        receiver.setName("gms-receiver");
        return List.of(sender, receiver);
    }

    @Override
    protected void onShutdown() {

    }

    private static class RequestTask {
        final GMSRequest packet;
        final CompletableFuture<GMSReply> future;

        RequestTask(GMSRequest packet, CompletableFuture<GMSReply> future) {
            this.packet = packet;
            this.future = future;
        }
    }

}
