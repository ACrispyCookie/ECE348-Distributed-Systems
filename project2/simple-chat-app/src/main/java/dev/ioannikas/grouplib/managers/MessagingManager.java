package dev.ioannikas.grouplib.managers;

import dev.ioannikas.grouplib.GroupAPI;
import dev.ioannikas.grouplib.network.serializable.PacketUDP;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSUpdateReply;

import java.util.List;
import java.util.concurrent.*;

public class MessagingManager extends GeneralManager {

    private final ConcurrentMap<Integer, BlockingQueue<PacketUDP>> groupPackets;

    public MessagingManager(GroupAPI api) {
        super(api);
        this.groupPackets = new ConcurrentHashMap<>();

        start();
    }

    public int send(int groupId, Object data) {
        PacketUDP packet = new PacketUDP(groupId, data);

        return 0;
    }

    public PacketUDP receive(int groupId, boolean block) throws InterruptedException {
        BlockingQueue<PacketUDP> queue = groupPackets.computeIfAbsent(groupId, i -> new LinkedBlockingQueue<>());

        PacketUDP packet;
        if (block)
            packet = queue.take();
        else
            packet = queue.poll();

        return packet;
    }

    public void handleReceivedPacket(PacketUDP reply) {
        if (reply.getObject() instanceof GMSUpdateReply) {
            groupPackets.computeIfAbsent(reply.getGroupId(), k -> new LinkedBlockingQueue<>()).add(reply);
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
