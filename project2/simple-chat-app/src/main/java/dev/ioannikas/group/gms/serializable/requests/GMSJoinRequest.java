package dev.ioannikas.group.gms.serializable.requests;

import java.io.Serial;

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
}
