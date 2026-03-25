package dev.ioannikas.group.gms.serializable.requests;

import dev.ioannikas.group.gms.serializable.PacketTCP;

import java.io.IOException;
import java.io.Serial;
import java.nio.ByteBuffer;

public class GMSLeaveRequest extends GMSRequest {

    @Serial
    private static final long serialVersionUID = 2L;
    private final int groupId;

    public GMSLeaveRequest(String username, int futureId, int groupId) {
        super(RequestType.LEAVE, username, futureId);
        this.groupId = groupId;
    }

    public int getGroupId() {
        return groupId;
    }

    public static GMSLeaveRequest deserialize(ByteBuffer buffer) {
        String username = PacketTCP.readString(buffer);
        int futureId = buffer.getInt();
        int groupId = buffer.getInt();
        return new GMSLeaveRequest(username, futureId, groupId);
    }

    public static void serialize(GMSLeaveRequest packet, ByteBuffer buffer) {
        buffer.putInt(packet.getGroupId());
    }
}
