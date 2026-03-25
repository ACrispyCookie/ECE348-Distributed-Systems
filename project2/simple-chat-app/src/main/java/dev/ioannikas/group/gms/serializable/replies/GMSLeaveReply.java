package dev.ioannikas.group.gms.serializable.replies;

import dev.ioannikas.group.api.LeaveReply;
import dev.ioannikas.group.gms.serializable.ClientInfo;

import java.io.Serial;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

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

    public static GMSLeaveReply deserialize(ByteBuffer buffer) {
        int groupId = buffer.getInt();
        int futureId = buffer.getInt();
        FailReason reason = FailReason.values()[buffer.getInt()];

        return new GMSLeaveReply(groupId, futureId, reason);
    }

    public static void serialize(GMSLeaveReply packet, ByteBuffer buffer) {
        buffer.putInt(packet.getFutureId());
        buffer.putInt(packet.getReason().ordinal());
    }

    public LeaveReply toApiVersion() {
        LeaveReply.FailReason replyReason = LeaveReply.FailReason.valueOf(reason.name());

        return new LeaveReply(getGroupId(), replyReason);
    }
}
