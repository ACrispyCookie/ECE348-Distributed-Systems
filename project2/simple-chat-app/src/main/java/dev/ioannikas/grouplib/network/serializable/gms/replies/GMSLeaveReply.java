package dev.ioannikas.grouplib.network.serializable.gms.replies;

import dev.ioannikas.grouplib.api.LeaveReply;

import java.io.Serial;

public class GMSLeaveReply extends GMSReply {

    @Serial
    private static final long serialVersionUID = 2L;
    private final FailReason reason;

    public GMSLeaveReply(int groupId, FailReason reason) {
        super(ReplyType.LEAVE, groupId);
        this.reason = reason;
    }

    public FailReason getReason() {
        return reason;
    }

    public enum FailReason {
        NONE,
        UNKNOWN
    }

    public LeaveReply toApiVersion() {
        LeaveReply.FailReason replyReason = LeaveReply.FailReason.valueOf(reason.name());

        return new LeaveReply(getGroupId(), replyReason);
    }
}
