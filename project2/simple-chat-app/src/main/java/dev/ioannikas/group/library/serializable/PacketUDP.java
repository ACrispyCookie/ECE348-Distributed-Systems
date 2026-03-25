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

    public static String readString(ByteBuffer buffer) {
        int length = buffer.getInt();
        if (length == -1) {
            return null;
        }

        byte[] stringBytes = new byte[length];
        buffer.get(stringBytes);
        return new String(stringBytes, StandardCharsets.UTF_8);
    }

    public static void writeString(ByteBuffer buffer, String str) {
        if (str == null) {
            buffer.putInt(-1);
        } else {
            byte[] stringBytes = str.getBytes(StandardCharsets.UTF_8);
            buffer.putInt(stringBytes.length);
            buffer.put(stringBytes);
        }
    }

    private enum PacketType {
        NORMAL
    }
}
