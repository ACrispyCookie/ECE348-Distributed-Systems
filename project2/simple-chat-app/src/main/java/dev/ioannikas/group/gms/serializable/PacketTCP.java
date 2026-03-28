package dev.ioannikas.group.gms.serializable;

import dev.ioannikas.group.gms.serializable.replies.GMSReply;
import dev.ioannikas.group.gms.serializable.requests.GMSRequest;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class PacketTCP implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;

    public static PacketTCP deserialize(ByteBuffer buffer) throws IOException, ClassNotFoundException {
        PacketType type = buffer.getInt() == PacketType.REQUEST.ordinal() ? PacketType.REQUEST : PacketType.REPLY;
        if (type == PacketType.REQUEST)
            return GMSRequest.deserialize(buffer);
        else
            return GMSReply.deserialize(buffer);
    }

    public static ByteBuffer serialize(PacketTCP packet) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(65536);
        buffer.putInt(packet instanceof GMSRequest ? PacketType.REQUEST.ordinal() : PacketType.REPLY.ordinal());
        if (packet instanceof GMSRequest req)
            GMSRequest.serialize(req, buffer);
        else
            GMSReply.serialize((GMSReply) packet, buffer);
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
        REQUEST,
        REPLY
    }
}
