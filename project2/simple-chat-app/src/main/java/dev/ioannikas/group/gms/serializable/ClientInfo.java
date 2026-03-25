package dev.ioannikas.group.gms.serializable;

import dev.ioannikas.group.gms.serializable.replies.GMSJoinReply;

import java.io.Serial;
import java.io.Serializable;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

public class ClientInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;
    private final String username;
    private final String ipAddress;
    private final int port;
    private transient SocketAddress address;

    public ClientInfo(String username, String ipAddress, int port) {
        this.username = username;
        this.ipAddress = ipAddress;
        this.port = port;
    }

    public ClientInfo(String username, String ipAddress, int port, SocketAddress address) {
        this.username = username;
        this.ipAddress = ipAddress;
        this.port = port;
        this.address = address;
    }

    public String getUsername() {
        return username;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    public SocketAddress getAddress() {
        return address;
    }

    public static ClientInfo deserialize(ByteBuffer buffer) {
        String username = PacketTCP.readString(buffer);
        String ipAddress = PacketTCP.readString(buffer);
        int port = buffer.getInt();

        return new ClientInfo(username, ipAddress, port);
    }

    public static void serialize(ClientInfo packet, ByteBuffer buffer) {
        PacketTCP.writeString(buffer, packet.getUsername());
        PacketTCP.writeString(buffer, packet.getIpAddress());
        buffer.putInt(packet.getPort());
    }

    @Override
    public String toString() {
        return "Username: " + username + " IP: " + ipAddress + " Port: " + port;
    }
}
