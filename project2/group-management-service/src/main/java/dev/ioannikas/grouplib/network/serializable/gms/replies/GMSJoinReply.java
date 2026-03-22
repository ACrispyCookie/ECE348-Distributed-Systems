package dev.ioannikas.grouplib.network.serializable.gms.replies;

import dev.ioannikas.grouplib.network.serializable.ClientInfo;

import java.io.Serial;
import java.util.List;

public class GMSJoinReply extends GMSReply {

    @Serial
    private static final long serialVersionUID = 2L;
    private final FailReason reason;
    private final List<ClientInfo> clients;

    public GMSJoinReply(int groupId, FailReason reason, List<ClientInfo> clients) {
        super(ReplyType.JOIN, groupId);
        this.reason = reason;
        this.clients = clients;
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
}
