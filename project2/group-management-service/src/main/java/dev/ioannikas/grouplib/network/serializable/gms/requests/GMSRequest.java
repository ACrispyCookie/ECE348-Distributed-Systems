package dev.ioannikas.grouplib.network.serializable.gms.requests;

import dev.ioannikas.grouplib.network.serializable.gms.PacketTCP;

import java.io.Serial;

public class GMSRequest extends PacketTCP {

    @Serial
    private static final long serialVersionUID = 2L;
    private final RequestType type;
    private final String username;
    private final int futureId;

    public GMSRequest(RequestType type, String username, int futureId) {
        this.type = type;
        this.username = username;
        this.futureId = futureId;
    }

    public RequestType getType() {
        return type;
    }

    public String getUsername() {
        return username;
    }

    public int getFutureId() {
        return futureId;
    }

    public static GMSRequest simpleHeartbeat() {
        return new GMSRequest(RequestType.HEARTBEAT, null, -1);
    }

    public enum RequestType {
        JOIN,
        LEAVE,
        HEARTBEAT
    }
}
