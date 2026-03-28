package dev.ioannikas.group.library.serializable.requests;

import dev.ioannikas.group.GroupAPI;
import dev.ioannikas.group.library.serializable.PacketUDP;

import java.io.Serial;
import java.nio.ByteBuffer;

public class NormalRequest extends PacketUDP {

    @Serial
    private static final long serialVersionUID = 2L;
    private final int groupId;
    private final Type type;
    protected final Object data;

    public NormalRequest(int groupId, Object data) {
        this.groupId = groupId;
        this.type = data instanceof BMRequest ? Type.BM_REQUEST : Type.BM_ACK;
        this.data = data;
    }

    public int getGroupId() {
        return groupId;
    }

    public Type getType() {
        return type;
    }

    public Object getData() {
        return data;
    }

    public static NormalRequest deserialize(GroupAPI api, ByteBuffer buffer) {
        int groupId = buffer.getInt();
        Type type = Type.values()[buffer.getInt()];

        Object request;
        if (type == Type.BM_REQUEST)
            request = BMRequest.deserialize(api, buffer);
        else
            request = BMAckRequest.deserialize(buffer);
        return new NormalRequest(groupId, request);
    }

    public static void serialize(NormalRequest packet, ByteBuffer buffer) {
        buffer.putInt(packet.getGroupId());
        buffer.putInt(packet.getType().ordinal());
        if (packet.type == Type.BM_REQUEST)
            BMRequest.serialize((BMRequest) packet.getData(), buffer);
        else
            BMAckRequest.serialize((BMAckRequest) packet.getData(), buffer);
    }

    enum Type {
        BM_REQUEST,
        BM_ACK
    }
}
