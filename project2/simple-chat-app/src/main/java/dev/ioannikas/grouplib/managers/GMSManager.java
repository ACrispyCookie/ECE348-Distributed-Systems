package dev.ioannikas.grouplib.managers;


import dev.ioannikas.chatapp.utility.Printer;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSJoinReply;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSLeaveReply;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSReply;
import dev.ioannikas.grouplib.network.serializable.gms.requests.GMSJoinRequest;
import dev.ioannikas.grouplib.network.serializable.gms.requests.GMSLeaveRequest;
import dev.ioannikas.grouplib.network.serializable.gms.requests.GMSRequest;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.*;

public class GMSManager {

    private final int HEARTBEAT_INTERVAL = 2;
    private boolean shuttingDown = false;

    private final Socket socket;
    private final BlockingQueue<RequestTask> outgoing;
    private final Queue<CompletableFuture<GMSReply>> pendingFutures;
    private final Thread sender, receiver;

    public GMSManager(String gmsAddress, int gmsPort) {
        this.outgoing = new LinkedBlockingQueue<>();
        this.pendingFutures = new ConcurrentLinkedQueue<>();

        this.sender = new Thread(this::sender);
        sender.setName("gms-sender");
        this.receiver = new Thread(this::receiver);
        receiver.setName("gms-receiver");

        Socket temp;
        try {
            temp = new Socket(gmsAddress, gmsPort);
        } catch (IOException e) {
            Printer.printErr("[GMS Manager] Error connecting to the GMS.");
            this.socket = null;
            shuttingDown = true;
            return;
        }

        this.socket = temp;
        sender.start();
        receiver.start();
    }

    public GMSJoinReply join(String groupName, String username, String ipAddress, int port) throws ExecutionException, InterruptedException {
        CompletableFuture<GMSReply> future = new CompletableFuture<>();

        GMSJoinRequest packet = new GMSJoinRequest(username, groupName, ipAddress, port);
        outgoing.put(new RequestTask(packet, future));

        return (GMSJoinReply) future.get();
    }

    public GMSLeaveReply leave(int groupId, String username) throws ExecutionException, InterruptedException {
        CompletableFuture<GMSReply> future = new CompletableFuture<>();

        GMSLeaveRequest packet = new GMSLeaveRequest(username, groupId);
        outgoing.put(new RequestTask(packet, future));

        return (GMSLeaveReply) future.get();
    }

    public void receiver() {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            while (!shuttingDown) {
                GMSReply reply = (GMSReply) in.readObject();
                CompletableFuture<GMSReply> future = pendingFutures.poll();

                if (future == null) {
                    // TODO Handle group view update
                    continue;
                }

                future.complete(reply);
            }
        } catch (Exception e) {
            if (!shuttingDown) e.printStackTrace();
            if (!shuttingDown) Printer.printErr("[GMS Receiver] GMS Disconnected.");
        }
    }

    private void sender() {
        try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {
            while (!shuttingDown) {
                RequestTask task = outgoing.poll(HEARTBEAT_INTERVAL, TimeUnit.SECONDS);

                if (task != null) {
                    pendingFutures.offer(task.future);

                    out.writeObject(task.packet);
                    out.flush();
                } else {
                    out.writeObject(GMSRequest.simpleHeartbeat());
                    out.flush();
                }
            }
        } catch (Exception e) {
            if (!shuttingDown) e.printStackTrace();
            if (!shuttingDown) Printer.printErr("[GMS Sender] GMS Disconnected.");
        }
    }

    public void shutdown() {
        shuttingDown = true;
        try {
            if (!socket.isClosed())
                socket.close();
        } catch (IOException e) {
            Printer.printErr("[GMS Manager] Error closing the socket.");
        }
        sender.interrupt();
        receiver.interrupt();
    }

    public boolean isShuttingDown() {
        return shuttingDown;
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
