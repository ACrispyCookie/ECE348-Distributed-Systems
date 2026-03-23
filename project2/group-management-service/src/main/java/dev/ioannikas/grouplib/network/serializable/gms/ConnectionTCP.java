package dev.ioannikas.grouplib.network.serializable.gms;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ConnectionTCP {

    private final Socket socket;
    private final ObjectInputStream in;
    private final ObjectOutputStream out;

    public ConnectionTCP(Socket socket) throws IOException {
        this.socket = socket;
        this.out = new ObjectOutputStream(socket.getOutputStream());
        this.in = new ObjectInputStream(socket.getInputStream());
    }

    public void close() throws IOException {
        in.close();
        out.close();
        socket.close();
    }

    public Object nextObject() throws IOException, ClassNotFoundException {
        return in.readObject();
    }

    public void sendPacket(PacketTCP packet) throws IOException {
        synchronized (out) {
            out.writeObject(packet);
            out.flush();
        }
    }

    public String getIp() {
        return socket.getInetAddress().getHostAddress();
    }

    public int getPort() {
        return socket.getPort();
    }

    public Socket getSocket() {
        return socket;
    }
}
