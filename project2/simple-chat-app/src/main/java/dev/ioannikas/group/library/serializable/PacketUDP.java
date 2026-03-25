package dev.ioannikas.group.library.serializable;

import java.io.*;
import java.nio.ByteBuffer;

public class PacketUDP implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;
    private static final int lengthFieldBytes = 4;
    private static int currentSeqNumber = 0;

    private final int groupId;
    private int seqNumber;

    public PacketUDP(int groupId) {
        this.groupId = groupId;
        this.seqNumber = -1;
    }

    public int getGroupId() {
        return groupId;
    }

    public int getSeqNumber() {
        return seqNumber;
    }

    public void setNextSeqNumber() {
        seqNumber = ++currentSeqNumber;
    }

    public static int getCurrentSeqNumber() {
        return currentSeqNumber;
    }

    public static PacketUDP deserialize(ByteBuffer buffer) throws IOException, ClassNotFoundException {
        if (buffer.remaining() < lengthFieldBytes)
            return null;

        int packetLength = buffer.getInt();

        if (buffer.remaining() < packetLength)
            return null;

        byte[] objectBytes = new byte[packetLength];
        buffer.get(objectBytes);

        ByteArrayInputStream bais = new ByteArrayInputStream(objectBytes);
        ObjectInputStream ois = new ObjectInputStream(bais);

        return (PacketUDP) ois.readObject();
    }

    public static ByteBuffer serialize(PacketUDP packet) throws IOException {
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
