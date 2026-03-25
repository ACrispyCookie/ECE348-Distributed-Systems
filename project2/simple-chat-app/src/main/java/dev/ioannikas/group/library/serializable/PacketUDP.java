package dev.ioannikas.group.library.serializable;

import dev.ioannikas.group.library.serializable.requests.GroupDataRequest;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class PacketUDP implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;

    public static PacketUDP deserialize(ByteBuffer buffer) throws IOException, ClassNotFoundException {
         PacketType type = PacketType.values()[buffer.getInt()];
        if (Objects.requireNonNull(type) == PacketType.NORMAL) {
            return GroupDataRequest.deserialize(buffer);
        }
        return null;
    }

    public static ByteBuffer serialize(PacketUDP packet) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        buffer.putInt(packet instanceof GroupDataRequest ? PacketType.NORMAL.ordinal() : -1);
        if (packet instanceof GroupDataRequest req)
            GroupDataRequest.serialize(req, buffer);
        return buffer.flip();
    }

    private enum PacketType {
        NORMAL
    }
}
