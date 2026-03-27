package dev.ioannikas.group.library.serializable.requests;

import dev.ioannikas.group.GroupAPI;
import dev.ioannikas.group.gms.serializable.PacketTCP;

import java.nio.ByteBuffer;

public class URMRequest {

    private final String sender;
    private final String messageId;
    private final Type type;
    private final Object data;

    public URMRequest(String sender, String messageId, Object data) {
        this.sender = sender;
        this.messageId = messageId;
        this.type = data instanceof TRMRequest ? Type.TRM_REQUEST : Type.TRM_NUMBER_REQUEST;
        this.data = data;
    }

    public String getSender() {
        return sender;
    }

    public String getMessageId() {
        return messageId;
    }

    public Object getData() {
        return data;
    }

    public static URMRequest deserialize(GroupAPI api, ByteBuffer buffer) {
        String sender = PacketTCP.readString(buffer);
        String sequenceNumber = PacketTCP.readString(buffer);
        Type type = Type.values()[buffer.getInt()];
        Object data;
        if (type == Type.TRM_REQUEST)
            data = TRMRequest.deserialize(api, buffer);
        else
            data = TRMNumberRequest.deserialize(buffer);
        return new URMRequest(sender, sequenceNumber, data);
    }

    public static void serialize(URMRequest packet, ByteBuffer buffer) {
        PacketTCP.writeString(buffer, packet.getSender());
        PacketTCP.writeString(buffer, packet.getMessageId());
        buffer.putInt(packet.type.ordinal());
        if (packet.type == Type.TRM_REQUEST)
            TRMRequest.serialize((TRMRequest) packet.getData(), buffer);
        else
            TRMNumberRequest.serialize((TRMNumberRequest) packet.getData(), buffer);
    }

    enum Type {
        TRM_REQUEST,
        TRM_NUMBER_REQUEST
    }
}
