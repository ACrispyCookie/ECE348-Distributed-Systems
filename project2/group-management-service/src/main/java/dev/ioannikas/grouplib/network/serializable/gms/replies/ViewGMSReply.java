package dev.ioannikas.grouplib.network.serializable.gms.replies;

import dev.ioannikas.grouplib.network.serializable.ClientInfo;

import java.io.Serial;

public class ViewGMSReply extends GMSReply {

    @Serial
    private static final long serialVersionUID = 1L;
    private final ClientInfo client;

    public ViewGMSReply(ReplyType type, ReplyResult result, int groupId, ClientInfo client) {
        super(type, result, groupId);
        this.client = client;
    }

    public ClientInfo getClient() {
        return client;
    }
}
