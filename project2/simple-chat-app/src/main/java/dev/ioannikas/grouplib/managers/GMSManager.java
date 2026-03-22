package dev.ioannikas.grouplib.managers;


import dev.ioannikas.chatapp.utility.Printer;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSReply;
import dev.ioannikas.grouplib.network.serializable.gms.replies.UpdateGMSReply;
import dev.ioannikas.grouplib.network.serializable.gms.requests.RequestGMSPacket;

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
    private final Queue<CompletableFuture<UpdateGMSReply>> pendingFutures;
    private final Thread sender, receiver;

    public GMSManager(String gmsAddress, int gmsPort) {
        try {
            this.socket = new Socket(gmsAddress, gmsPort);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.outgoing = new LinkedBlockingQueue<>();
        this.pendingFutures = new ConcurrentLinkedQueue<>();

        this.sender = new Thread(this::sender);
        sender.setName("gms-sender");
        sender.start();
        this.receiver = new Thread(this::receiver);
        receiver.setName("gms-receiver");
        receiver.start();
    }

    public UpdateGMSReply join(String groupName, String username, String ipAddress, int port) throws ExecutionException, InterruptedException {
        CompletableFuture<UpdateGMSReply> future = new CompletableFuture<>();

        RequestGMSPacket packet = new RequestGMSPacket(RequestGMSPacket.RequestType.JOIN, username, -1, groupName, ipAddress, port);
        outgoing.put(new RequestTask(packet, future));

        return future.get();
    }

    public UpdateGMSReply leave(int groupId, String username) throws ExecutionException, InterruptedException {
        CompletableFuture<UpdateGMSReply> future = new CompletableFuture<>();

        RequestGMSPacket packet = new RequestGMSPacket(RequestGMSPacket.RequestType.LEAVE, username, groupId, null, null, -1);
        outgoing.put(new RequestTask(packet, future));

        return future.get();
    }

    public void receiver() {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            while (!shuttingDown) {
                GMSReply reply = (GMSReply) in.readObject();
                CompletableFuture<UpdateGMSReply> future = pendingFutures.poll();
                Printer.printDebug("Received reply " + reply.getType());

                if (future == null) {
                    // TODO Handle group view update
                    continue;
                }

                future.complete((UpdateGMSReply) reply);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (!shuttingDown) Printer.printErr("GMS Disconnected.");
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
                    out.writeObject(RequestGMSPacket.simpleHeartbeat());
                    out.flush();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (!shuttingDown) Printer.printErr("GMS Disconnected.");
        }
    }

    public void shutdown() {
        shuttingDown = true;
        try {
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        sender.interrupt();
        receiver.interrupt();
    }

    private static class RequestTask {
        final RequestGMSPacket packet;
        final CompletableFuture<UpdateGMSReply> future;

        RequestTask(RequestGMSPacket packet, CompletableFuture<UpdateGMSReply> future) {
            this.packet = packet;
            this.future = future;
        }
    }

}
