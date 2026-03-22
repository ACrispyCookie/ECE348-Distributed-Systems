package dev.ioannikas.grouplib.network.serializable.gms.replies;

import dev.ioannikas.grouplib.network.serializable.ClientInfo;

import java.io.Serial;
import java.util.List;

public class UpdateGMSReply extends GMSReply {

    @Serial
    private static final long serialVersionUID = 1L;
    private final FailReason reason;
    private final List<ClientInfo> clients;

    public UpdateGMSReply(ReplyType type, ReplyResult result, int groupId, FailReason reason, List<ClientInfo> clients) {
        super(type, result, groupId);
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
        USERNAME_TAKEN
    }
}
