package dev.ioannikas.group.library.serializable.requests;

import dev.ioannikas.group.library.serializable.PacketUDP;

import java.io.Serial;
import java.nio.ByteBuffer;

public class NormalRequest extends PacketUDP {

    @Serial
    private static final long serialVersionUID = 2L;
    private final int groupId;
    protected final Object data;

    public NormalRequest(int groupId, Object data) {
        this.groupId = groupId;
        this.data = data;
    }

    public int getGroupId() {
        return groupId;
    }

    public Object getData() {
        return data;
    }

    public static PacketUDP deserialize(ByteBuffer buffer) {
        return GroupDataRequest.deserialize(buffer);
    }

    public static void serialize(NormalRequest packet, ByteBuffer buffer) {
        buffer.putInt(packet.getGroupId());
        GroupDataRequest.serialize((GroupDataRequest) packet, buffer);
    }
}
