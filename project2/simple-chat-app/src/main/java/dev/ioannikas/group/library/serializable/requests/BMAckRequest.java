package dev.ioannikas.group.library.serializable.requests;

import dev.ioannikas.group.gms.serializable.PacketTCP;

import java.nio.ByteBuffer;

public class BMAckRequest {

    private final String sender;
    private final int sequenceNumber;

    public BMAckRequest(String sender, int sequenceNumber) {
        this.sender = sender;
        this.sequenceNumber = sequenceNumber;
    }

    public String getSender() {
        return sender;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public static BMAckRequest deserialize(ByteBuffer buffer) {
        String info = PacketTCP.readString(buffer);
        int sequenceNumber = buffer.getInt();
        return new BMAckRequest(info, sequenceNumber);
    }

    public static void serialize(BMAckRequest packet, ByteBuffer buffer) {
        PacketTCP.writeString(buffer, packet.getSender());
        buffer.putInt(packet.sequenceNumber);
    }
}
