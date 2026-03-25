package dev.ioannikas.group.gms.serializable.requests;

import dev.ioannikas.group.gms.serializable.PacketTCP;

import java.io.Serial;
import java.nio.ByteBuffer;

public class GMSJoinRequest extends GMSRequest {

    @Serial
    private static final long serialVersionUID = 2L;
    private final String groupName;
    private final String ipAddress;
    private final int port;

    public GMSJoinRequest(String username, int futureId, String groupName, String ipAddress, int port) {
        super(RequestType.JOIN, username, futureId);
        this.groupName = groupName;
        this.ipAddress = ipAddress;
        this.port = port;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    public static GMSJoinRequest deserialize(ByteBuffer buffer) {
        String username = PacketTCP.readString(buffer);
        int futureId = buffer.getInt();
        String groupName = PacketTCP.readString(buffer);
        String ipAddress = PacketTCP.readString(buffer);
        int port = buffer.getInt();
        return new GMSJoinRequest(username, futureId, groupName, ipAddress, port);
    }

    public static void serialize(GMSJoinRequest packet, ByteBuffer buffer) {
        PacketTCP.writeString(buffer, packet.getGroupName());
        PacketTCP.writeString(buffer, packet.getIpAddress());
        buffer.putInt(packet.getPort());
    }
}
