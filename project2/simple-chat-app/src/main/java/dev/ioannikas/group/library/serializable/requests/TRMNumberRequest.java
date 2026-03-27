package dev.ioannikas.group.library.serializable.requests;

import dev.ioannikas.group.gms.serializable.PacketTCP;

import java.nio.ByteBuffer;

public class TRMNumberRequest {

    private final String messageId;
    private final int deliveryNumber;

    public TRMNumberRequest(String messageId, int deliveryNumber) {
        this.messageId = messageId;
        this.deliveryNumber = deliveryNumber;
    }

    public String getMessageId() {
        return messageId;
    }

    public int getDeliveryNumber() {
        return deliveryNumber;
    }

    public static TRMNumberRequest deserialize(ByteBuffer buffer) {
        String sequenceNumber = PacketTCP.readString(buffer);
        int deliveryNumber = buffer.getInt();
        return new TRMNumberRequest(sequenceNumber, deliveryNumber);
    }

    public static void serialize(TRMNumberRequest packet, ByteBuffer buffer) {
        PacketTCP.writeString(buffer, packet.getMessageId());
        buffer.putInt(packet.getDeliveryNumber());
    }
}
