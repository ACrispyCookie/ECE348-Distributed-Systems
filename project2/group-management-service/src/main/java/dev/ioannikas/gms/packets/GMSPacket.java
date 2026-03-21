package dev.ioannikas.gms.packets;

import java.io.Serializable;

public class GMSPacket implements Serializable {

    private final PacketType type;
    private final String username;

    /* Used on LEAVE packets */
    private final int groupId;

    /* Used on JOIN packets */
    private final String groupName;
    private final String ipAddress;
    private final int port;

    public GMSPacket(PacketType type, String username, int groupId, String groupName, String ipAddress, int port) {
        this.type = type;
        this.username = username;
        this.groupId = groupId;
        this.groupName = groupName;
        this.ipAddress = ipAddress;
        this.port = port;
    }

    public PacketType getType() {
        return type;
    }

    public String getUsername() {
        return username;
    }

    public int getGroupId() {
        return groupId;
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

    public enum PacketType {
        JOIN,
        LEAVE,
        HEARTBEAT
    }
}
