package dev.ioannikas.grouplib.network.serializable.gms.replies;

import dev.ioannikas.grouplib.network.serializable.Packet;

import java.io.Serial;

public class GMSReply extends Packet {

    @Serial
    private static final long serialVersionUID = 1L;
    private final ReplyType type;
    private final ReplyResult result;
    private final int groupId;

    public GMSReply(ReplyType type, ReplyResult result, int groupId) {
        this.type = type;
        this.result = result;
        this.groupId = groupId;
    }

    public ReplyType getType() {
        return type;
    }

    public ReplyResult getResult() {
        return result;
    }

    public int getGroupId() {
        return groupId;
    }

    public enum ReplyType {
        UPDATE,
        JOIN,
        LEAVE
    }

    public enum ReplyResult {
        SUCCESS,
        ERROR
    }
}
