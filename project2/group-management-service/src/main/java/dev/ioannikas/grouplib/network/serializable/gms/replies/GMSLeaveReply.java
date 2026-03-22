package dev.ioannikas.grouplib.network.serializable.gms.replies;

import java.io.Serial;

public class GMSLeaveReply extends GMSReply {

    @Serial
    private static final long serialVersionUID = 2L;
    private final int futureId;
    private final FailReason reason;

    public GMSLeaveReply(int groupId, int futureId, FailReason reason) {
        super(ReplyType.LEAVE, groupId);
        this.futureId = futureId;
        this.reason = reason;
    }

    public int getFutureId() {
        return futureId;
    }

    public FailReason getReason() {
        return reason;
    }

    public enum FailReason {
        NONE,
        UNKNOWN
    }
}
