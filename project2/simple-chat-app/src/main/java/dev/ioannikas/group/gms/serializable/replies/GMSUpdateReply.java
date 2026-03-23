package dev.ioannikas.group.gms.serializable.replies;

import dev.ioannikas.group.api.ViewUpdateMessage;

import java.io.Serial;

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

    public ViewUpdateMessage toApiVersion() {
        return new ViewUpdateMessage(getGroupId(), ViewUpdateMessage.UpdateAction.valueOf(getClientAction().name()), getClient());
    }
}
