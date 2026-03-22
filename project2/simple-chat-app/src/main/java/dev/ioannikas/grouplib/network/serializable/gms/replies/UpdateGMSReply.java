package dev.ioannikas.grouplib.network.serializable.gms.replies;

import dev.ioannikas.grouplib.api.JoinLeaveResult;
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

    public JoinLeaveResult toApiVersion() {
        JoinLeaveResult.Status status = JoinLeaveResult.Status.valueOf(this.getResult().name());
        JoinLeaveResult.FailReason failReason = JoinLeaveResult.FailReason.valueOf(reason.name());

        return new JoinLeaveResult(status, getGroupId(), failReason, clients.stream().map(ClientInfo::getUsername).toList());
    }

    public enum FailReason {
        USERNAME_TAKEN
    }
}
