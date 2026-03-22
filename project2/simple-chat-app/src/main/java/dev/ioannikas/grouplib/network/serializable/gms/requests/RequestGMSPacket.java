package dev.ioannikas.grouplib.network.serializable.gms.requests;

import dev.ioannikas.grouplib.network.serializable.Packet;

public class RequestGMSPacket extends Packet {

    private final RequestType type;
    private final String username;

    /* Used on LEAVE packets */
    private final int groupId;

    /* Used on JOIN packets */
    private final String groupName;
    private final String ipAddress;
    private final int port;

    public RequestGMSPacket(RequestType type, String username, int groupId, String groupName, String ipAddress, int port) {
        this.type = type;
        this.username = username;
        this.groupId = groupId;
        this.groupName = groupName;
        this.ipAddress = ipAddress;
        this.port = port;
    }

    public RequestType getType() {
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

    public static RequestGMSPacket simpleHeartbeat() {
        return new RequestGMSPacket(RequestType.HEARTBEAT, null, -1, null, null, -1);
    }

    public enum RequestType {
        JOIN,
        LEAVE,
        HEARTBEAT
    }
}
