package dev.ioannikas.group.gms.serializable.replies;

import dev.ioannikas.group.api.JoinReply;
import dev.ioannikas.group.gms.serializable.ClientInfo;

import java.io.Serial;
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

    public JoinReply toApiVersion() {
        List<String> clientNames = clients == null ? null : clients.stream().map(ClientInfo::getUsername).toList();
        JoinReply.FailReason replyReason = JoinReply.FailReason.valueOf(reason.name());

        return new JoinReply(getGroupId(), replyReason, clientNames);
    }
}
