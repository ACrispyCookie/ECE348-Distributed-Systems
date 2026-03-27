package dev.ioannikas.group.library.serializable.requests;

import dev.ioannikas.group.GroupAPI;
import dev.ioannikas.group.api.SerializableObject;
import dev.ioannikas.group.gms.serializable.PacketTCP;

import java.nio.ByteBuffer;

public class TRMRequest {

    private final String sequenceNumber;
    private final SerializableObject data;

    public TRMRequest(String sequenceNumber, SerializableObject data) {
        this.sequenceNumber = sequenceNumber;
        this.data = data;
    }

    public String getSequenceNumber() {
        return sequenceNumber;
    }

    public SerializableObject getData() {
        return data;
    }

    public static TRMRequest deserialize(ByteBuffer buffer) {
        String sequenceNumber = PacketTCP.readString(buffer);
        SerializableObject data = GroupAPI.getDeserializer().apply(buffer);
        return new TRMRequest(sequenceNumber, data);
    }

    public static void serialize(TRMRequest packet, ByteBuffer buffer) {
        PacketTCP.writeString(buffer, packet.getSequenceNumber());
        packet.data.serialize(buffer);
    }
}
