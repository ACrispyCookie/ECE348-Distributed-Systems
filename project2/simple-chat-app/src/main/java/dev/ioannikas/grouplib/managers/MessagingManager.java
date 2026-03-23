package dev.ioannikas.grouplib.managers;

import dev.ioannikas.grouplib.GroupAPI;
import dev.ioannikas.grouplib.network.serializable.group.PacketUDP;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSUpdateReply;
import dev.ioannikas.grouplib.network.serializable.group.requests.NormalRequest;

import java.util.List;
import java.util.concurrent.*;

public class MessagingManager extends GeneralManager {

    private final ConcurrentMap<Integer, BlockingQueue<NormalRequest>> groupPackets;

    public MessagingManager(GroupAPI api) {
        super(api);
        this.groupPackets = new ConcurrentHashMap<>();

        start();
    }

    public int send(int groupId, Object data) {
        NormalRequest packet = new NormalRequest(groupId, data);

        return 0;
    }

    public NormalRequest receive(int groupId, boolean block) throws InterruptedException {
        BlockingQueue<NormalRequest> queue = groupPackets.computeIfAbsent(groupId, i -> new LinkedBlockingQueue<>());

        NormalRequest packet;
        if (block)
            packet = queue.take();
        else
            packet = queue.poll();

        return packet;
    }

    public void handleReceivedPacket(PacketUDP reply) {
        if (reply instanceof NormalRequest normal && normal.getData() instanceof GMSUpdateReply) {
            groupPackets.computeIfAbsent(reply.getGroupId(), k -> new LinkedBlockingQueue<>()).add(normal);
            return;
        } else {

        }
    }

    @Override
    protected List<Thread> getThreads() {
        return List.of();
    }

    @Override
    protected void onShutdown() {

    }
}
