package dev.ioannikas.grouplib.network.serializable.gms.replies;

import dev.ioannikas.grouplib.network.serializable.ClientInfo;

import java.io.Serial;

public class GMSUpdateReply extends GMSReply {

    @Serial
    private static final long serialVersionUID = 2L;
    private final ClientInfo client;
    private final Action clientAction;

    public GMSUpdateReply(int groupId, ClientInfo client, Action clientAction) {
        super(ReplyType.UPDATE, groupId);
        this.client = client;
        this.clientAction = clientAction;
    }

    public ClientInfo getClient() {
        return client;
    }

    public Action getClientAction() {
        return clientAction;
    }

    public enum Action {
        JOINED,
        LEFT
    }
}
