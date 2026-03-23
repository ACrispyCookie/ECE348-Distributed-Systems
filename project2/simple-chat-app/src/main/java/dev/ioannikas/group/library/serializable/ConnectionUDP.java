package dev.ioannikas.group.library.serializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramSocket;

public class ConnectionUDP {

    private final DatagramSocket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;

    public ConnectionUDP(DatagramSocket socket) throws IOException {
        this.socket = socket;
    }

    public void close() throws IOException {
        in.close();
        out.close();
        socket.close();
    }

    public Object nextObject() throws IOException, ClassNotFoundException {
        return in.readObject();
    }

    public void sendPacket(PacketUDP packet) throws IOException {
        out.writeObject(packet);
        out.flush();
    }

    public String getIp() {
        return socket.getInetAddress().getHostAddress();
    }

    public int getPort() {
        return socket.getPort();
    }

    public DatagramSocket getSocket() {
        return socket;
    }
}
