package dev.ioannikas.group.gms.serializable;

import java.io.*;
import java.nio.ByteBuffer;

public class PacketTCP implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;
    private static final int lengthFieldBytes = 4;

    public static PacketTCP deserialize(byte[] array, int bytesRead) throws IOException, ClassNotFoundException {
        if (bytesRead < lengthFieldBytes)
            return null;

        ByteBuffer buffer = ByteBuffer.wrap(array);
        int packetLength = buffer.getInt();

        if (bytesRead - lengthFieldBytes < packetLength)
            return null;

        ByteArrayInputStream bais = new ByteArrayInputStream(array, lengthFieldBytes, bytesRead);
        ObjectInputStream ois = new ObjectInputStream(bais);

        return (PacketTCP) ois.readObject();
    }

    public static ByteBuffer serialize(PacketTCP packet) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(packet);
        oos.flush();
        byte[] objectBytes = baos.toByteArray();

        ByteBuffer buffer = ByteBuffer.allocate(4 + objectBytes.length);
        buffer.putInt(objectBytes.length);
        buffer.put(objectBytes);
        buffer.flip();
        return buffer;
    }
}
