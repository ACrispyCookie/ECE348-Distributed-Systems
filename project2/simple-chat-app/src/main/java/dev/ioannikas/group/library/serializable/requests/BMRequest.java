package dev.ioannikas.group.library.serializable.requests;

import dev.ioannikas.group.GroupAPI;
import dev.ioannikas.group.gms.serializable.PacketTCP;

import java.nio.ByteBuffer;

public class BMRequest {

    private final String sender;
    private final int sequenceNumber;
    private final URMRequest data;

    public BMRequest(String sender, int sequenceNumber, URMRequest data) {
        this.sender = sender;
        this.sequenceNumber = sequenceNumber;
        this.data = data;
    }

    public String getSender() {
        return sender;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public URMRequest getData() {
        return data;
    }

    public static BMRequest deserialize(GroupAPI api, ByteBuffer buffer) {
        String info = PacketTCP.readString(buffer);
        int sequenceNumber = buffer.getInt();
        URMRequest request = URMRequest.deserialize(api, buffer);
        return new BMRequest(info, sequenceNumber, request);
    }

    public static void serialize(BMRequest packet, ByteBuffer buffer) {
        PacketTCP.writeString(buffer, packet.getSender());
        buffer.putInt(packet.sequenceNumber);
        URMRequest.serialize(packet.getData(), buffer);

    }
}
