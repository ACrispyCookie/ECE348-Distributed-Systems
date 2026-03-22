package dev.ioannikas.grouplib.managers;

import dev.ioannikas.grouplib.GroupAPI;
import dev.ioannikas.grouplib.api.Message;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSUpdateReply;

import java.util.List;
import java.util.concurrent.*;

public class MessagingManager extends GeneralManager {

    private final ConcurrentMap<Integer, BlockingQueue<Message>> groupPackets;

    public MessagingManager(GroupAPI api) {
        super(api);
        this.groupPackets = new ConcurrentHashMap<>();

        start();
    }

    public Message receive(int groupId, boolean block) {
        try {
            BlockingQueue<Message> queue = groupPackets.get(groupId);

            Message message;
            if (block)
                message = queue.take();
            else
                message = queue.poll();

            return message;
        } catch (InterruptedException ignored) {

        }

        return null;
    }

    public void handleReceivedPacket(Message reply) {
        if (reply.getType() == Message.Type.VIEW_UPDATE) {
            GMSUpdateReply update = (GMSUpdateReply) reply.getObject();
            groupPackets.computeIfAbsent(update.getGroupId(), k -> new LinkedBlockingQueue<>()).add(reply);

            return;
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
