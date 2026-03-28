package dev.ioannikas.group.library.serializable.requests;

import dev.ioannikas.group.gms.serializable.PacketTCP;

import java.nio.ByteBuffer;

public class TRMSyncRequest {

    private final String name;

    public TRMSyncRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static TRMSyncRequest deserialize(ByteBuffer buffer) {
        String name = PacketTCP.readString(buffer);
        return new TRMSyncRequest(name);
    }

    public static void serialize(TRMSyncRequest packet, ByteBuffer buffer) {
        PacketTCP.writeString(buffer, packet.getName());
    }
}
