package dev.ioannikas.group.gms.serializable.requests;

import dev.ioannikas.group.gms.GMS;
import dev.ioannikas.group.gms.serializable.PacketTCP;
import dev.ioannikas.group.gms.serializable.replies.GMSReply;

import java.io.IOException;
import java.io.Serial;
import java.nio.ByteBuffer;

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

    public static PacketTCP deserialize(ByteBuffer buffer) {
        RequestType type = RequestType.values()[buffer.getInt()];
        switch (type) {
            case JOIN -> {
                return GMSJoinRequest.deserialize(buffer);
            }
            case LEAVE -> {
                return GMSLeaveRequest.deserialize(buffer);
            }
            case HEARTBEAT -> {
                String username = PacketTCP.readString(buffer);
                int futureId = buffer.getInt();
                return new GMSRequest(RequestType.HEARTBEAT, username, futureId);
            }
            default -> {
                return null;
            }
        }
    }

    public static void serialize(GMSRequest packet, ByteBuffer buffer) {
        buffer.putInt(packet.getType().ordinal());
        PacketTCP.writeString(buffer, packet.getUsername());
        buffer.putInt(packet.getFutureId());

        switch (packet.getType()) {
            case JOIN -> GMSJoinRequest.serialize((GMSJoinRequest) packet, buffer);
            case LEAVE -> GMSLeaveRequest.serialize((GMSLeaveRequest) packet, buffer);
        }
    }
}
