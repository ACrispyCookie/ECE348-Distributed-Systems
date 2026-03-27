package dev.ioannikas.group.library.serializable.requests;

import dev.ioannikas.group.GroupAPI;
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

    public static NormalRequest deserialize(GroupAPI api, ByteBuffer buffer) {
        int groupId = buffer.getInt();
        URMRequest request = URMRequest.deserialize(api, buffer);
        return new NormalRequest(groupId, request);
    }

    public static void serialize(NormalRequest packet, ByteBuffer buffer) {
        buffer.putInt(packet.getGroupId());
        URMRequest.serialize((URMRequest) packet.getData(), buffer);
    }
}
