package dev.ioannikas.group.library.serializable.requests;

import dev.ioannikas.group.GroupAPI;
import dev.ioannikas.group.api.SerializableObject;
import dev.ioannikas.group.gms.serializable.PacketTCP;

import java.nio.ByteBuffer;

public class TRMRequest {

    private final String messageId;
    private final SerializableObject data;

    public TRMRequest(String messageId, SerializableObject data) {
        this.messageId = messageId;
        this.data = data;
    }

    public String getMessageId() {
        return messageId;
    }

    public SerializableObject getData() {
        return data;
    }

    public static TRMRequest deserialize(GroupAPI api, ByteBuffer buffer) {
        String sequenceNumber = PacketTCP.readString(buffer);
        SerializableObject data = api.getDeserializer().apply(buffer);
        return new TRMRequest(sequenceNumber, data);
    }

    public static void serialize(TRMRequest packet, ByteBuffer buffer) {
        PacketTCP.writeString(buffer, packet.getMessageId());
        packet.data.serialize(buffer);
    }
}
