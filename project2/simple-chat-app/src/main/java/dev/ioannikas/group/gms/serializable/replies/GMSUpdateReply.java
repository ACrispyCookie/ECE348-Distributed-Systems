package dev.ioannikas.group.gms.serializable.replies;

import dev.ioannikas.group.api.ViewUpdateMessage;
import dev.ioannikas.group.gms.serializable.PacketTCP;

import java.io.Serial;
import java.nio.ByteBuffer;

public class GMSUpdateReply extends GMSReply {

    @Serial
    private static final long serialVersionUID = 2L;
    private final String username;
    private final Action clientAction;

    public GMSUpdateReply(int groupId, String username, Action clientAction) {
        super(ReplyType.UPDATE, groupId);
        this.username = username;
        this.clientAction = clientAction;
    }

    public String getClient() {
        return username;
    }

    public Action getClientAction() {
        return clientAction;
    }

    public enum Action {
        JOINED,
        LEFT
    }

    public static GMSUpdateReply deserialize(ByteBuffer buffer) {
        int groupId = buffer.getInt();
        String username = PacketTCP.readString(buffer);
        Action action = Action.values()[buffer.getInt()];

        return new GMSUpdateReply(groupId, username, action);
    }

    public static void serialize(GMSUpdateReply packet, ByteBuffer buffer) {
        PacketTCP.writeString(buffer, packet.getClient());
        buffer.putInt(packet.getClientAction().ordinal());
    }

    public ViewUpdateMessage toApiVersion() {
        return new ViewUpdateMessage(getGroupId(), ViewUpdateMessage.UpdateAction.valueOf(getClientAction().name()), getClient());
    }
}
