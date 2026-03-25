package dev.ioannikas.group.gms.serializable.replies;

import dev.ioannikas.group.gms.serializable.PacketTCP;
import dev.ioannikas.group.gms.serializable.requests.GMSJoinRequest;
import dev.ioannikas.group.gms.serializable.requests.GMSLeaveRequest;
import dev.ioannikas.group.gms.serializable.requests.GMSRequest;

import java.io.IOException;
import java.io.Serial;
import java.nio.ByteBuffer;

public abstract class GMSReply extends PacketTCP {

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


    public static PacketTCP deserialize(ByteBuffer buffer) {
        ReplyType type = ReplyType.values()[buffer.getInt()];
        switch (type) {
            case JOIN -> {
                return GMSJoinReply.deserialize(buffer);
            }
            case LEAVE -> {
                return GMSLeaveReply.deserialize(buffer);
            }
            case UPDATE -> {
                return GMSUpdateReply.deserialize(buffer);
            }
            default -> {
                return null;
            }
        }
    }

    public static void serialize(GMSReply packet, ByteBuffer buffer) {
        buffer.putInt(packet.getType().ordinal());
        buffer.putInt(packet.getGroupId());

        switch (packet.getType()) {
            case JOIN -> GMSJoinReply.serialize((GMSJoinReply) packet, buffer);
            case LEAVE -> GMSLeaveReply.serialize((GMSLeaveReply) packet, buffer);
            case UPDATE -> GMSUpdateReply.serialize((GMSUpdateReply) packet, buffer);
        }
    }
}
