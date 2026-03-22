package dev.ioannikas.grouplib.network.serializable.gms.replies;

import dev.ioannikas.grouplib.network.serializable.gms.PacketTCP;

import java.io.Serial;

public class GMSReply extends PacketTCP {

    @Serial
    private static final long serialVersionUID = 2L;
    private final ReplyType type;
    private final int groupId;

    public GMSReply(ReplyType type, int groupId) {
        this.type = type;
        this.groupId = groupId;
    }

    public ReplyType getType() {
        return type;
    }

    public int getGroupId() {
        return groupId;
    }

    public enum ReplyType {
        UPDATE,
        JOIN,
        LEAVE
    }
}
