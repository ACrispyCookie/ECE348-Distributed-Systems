package dev.ioannikas.group.gms.serializable.replies;

import dev.ioannikas.group.api.ViewUpdateMessage;
import dev.ioannikas.group.gms.serializable.ClientInfo;

import java.io.Serial;
import java.nio.ByteBuffer;

public class GMSUpdateReply extends GMSReply {

    @Serial
    private static final long serialVersionUID = 2L;
    private final ClientInfo user;
    private final Action clientAction;

    public GMSUpdateReply(int groupId, ClientInfo user, Action clientAction) {
        super(ReplyType.UPDATE, groupId);
        this.user = user;
        this.clientAction = clientAction;
    }

    public ClientInfo getClient() {
        return user;
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
        ClientInfo user = ClientInfo.deserialize(buffer);
        Action action = Action.values()[buffer.getInt()];

        return new GMSUpdateReply(groupId, user, action);
    }

    public static void serialize(GMSUpdateReply packet, ByteBuffer buffer) {
        ClientInfo.serialize(packet.getClient(), buffer);
        buffer.putInt(packet.getClientAction().ordinal());
    }

    public ViewUpdateMessage toApiVersion() {
        return new ViewUpdateMessage(getGroupId(), ViewUpdateMessage.UpdateAction.valueOf(getClientAction().name()), getClient().getUsername());
    }
}
