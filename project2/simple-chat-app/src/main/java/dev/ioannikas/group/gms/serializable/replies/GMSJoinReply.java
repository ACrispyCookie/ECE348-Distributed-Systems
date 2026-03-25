package dev.ioannikas.group.gms.serializable.replies;

import dev.ioannikas.group.api.JoinReply;
import dev.ioannikas.group.gms.serializable.ClientInfo;
import dev.ioannikas.group.gms.serializable.PacketTCP;
import dev.ioannikas.group.gms.serializable.requests.GMSJoinRequest;

import java.io.Serial;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class GMSJoinReply extends GMSReply {

    @Serial
    private static final long serialVersionUID = 2L;
    private final int futureId;
    private final FailReason reason;
    private final List<ClientInfo> clients;

    public GMSJoinReply(int groupId, int futureId, FailReason reason, List<ClientInfo> clients) {
        super(ReplyType.JOIN, groupId);
        this.futureId = futureId;
        this.reason = reason;
        this.clients = clients;
    }

    public int getFutureId() {
        return futureId;
    }

    public FailReason getReason() {
        return reason;
    }

    public List<ClientInfo> getClients() {
        return clients;
    }

    public enum FailReason {
        NONE,
        USERNAME_TAKEN,
        UNKNOWN
    }

    public static GMSJoinReply deserialize(ByteBuffer buffer) {
        int groupId = buffer.getInt();
        int futureId = buffer.getInt();
        FailReason reason = FailReason.values()[buffer.getInt()];
        int clientCount = buffer.getInt();

        List<ClientInfo> clients = new LinkedList<>();
        for (int i = 0; i < clientCount; i++)
            clients.add(ClientInfo.deserialize(buffer));

        return new GMSJoinReply(groupId, futureId, reason, clients);
    }

    public static void serialize(GMSJoinReply packet, ByteBuffer buffer) {
        buffer.putInt(packet.getFutureId());
        buffer.putInt(packet.getReason().ordinal());
        buffer.putInt(packet.getClients() == null ? 0 : packet.getClients().size());

        if (packet.getClients() == null)
            return;

        for (ClientInfo info : packet.getClients())
            ClientInfo.serialize(info, buffer);
    }

    public JoinReply toApiVersion() {
        List<String> clientNames = clients == null ? null : clients.stream().map(ClientInfo::getUsername).toList();
        JoinReply.FailReason replyReason = JoinReply.FailReason.valueOf(reason.name());

        return new JoinReply(getGroupId(), replyReason, clientNames);
    }
}
