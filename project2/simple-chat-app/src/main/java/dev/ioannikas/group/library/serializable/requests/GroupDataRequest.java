package dev.ioannikas.group.library.serializable.requests;

import dev.ioannikas.group.GroupAPI;
import dev.ioannikas.group.api.SerializableObject;
import dev.ioannikas.group.library.serializable.PacketUDP;

import java.nio.ByteBuffer;

public class GroupDataRequest extends NormalRequest {

    private final int seqNumber;

    public GroupDataRequest(int groupId, SerializableObject data, int seqNumber) {
        super(groupId, data);
        this.seqNumber = seqNumber;
    }

    public int getSeqNumber() {
        return seqNumber;
    }

    public SerializableObject getData() {
        return (SerializableObject) data;
    }

    public static PacketUDP deserialize(ByteBuffer buffer) {
        int groupId = buffer.getInt();
        int seqNum = buffer.getInt();
        SerializableObject object = GroupAPI.getDeserializer().apply(buffer);

        return new GroupDataRequest(groupId, object, seqNum);
    }

    public static void serialize(GroupDataRequest packet, ByteBuffer buffer) {
        buffer.putInt(packet.getSeqNumber());
        packet.getData().serialize(buffer);
    }
}
