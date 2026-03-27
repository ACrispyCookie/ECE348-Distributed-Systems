package dev.ioannikas.group.library.serializable.requests;

import dev.ioannikas.group.gms.serializable.PacketTCP;

import java.nio.ByteBuffer;

public class TRMNumberRequest {

    private final String sequenceNumber;
    private final String deliveryNumber;

    public TRMNumberRequest(String sequenceNumber, String deliveryNumber) {
        this.sequenceNumber = sequenceNumber;
        this.deliveryNumber = deliveryNumber;
    }

    public String getSequenceNumber() {
        return sequenceNumber;
    }

    public String getDeliveryNumber() {
        return deliveryNumber;
    }

    public static TRMNumberRequest deserialize(ByteBuffer buffer) {
        String sequenceNumber = PacketTCP.readString(buffer);
        String deliveryNumber = PacketTCP.readString(buffer);
        return new TRMNumberRequest(sequenceNumber, deliveryNumber);
    }

    public static void serialize(TRMNumberRequest packet, ByteBuffer buffer) {
        PacketTCP.writeString(buffer, packet.getSequenceNumber());
        PacketTCP.writeString(buffer, packet.getDeliveryNumber());
    }
}
