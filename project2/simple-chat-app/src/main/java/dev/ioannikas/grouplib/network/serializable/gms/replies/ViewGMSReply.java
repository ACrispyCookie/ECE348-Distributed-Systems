package dev.ioannikas.grouplib.network.serializable.gms.replies;

import dev.ioannikas.grouplib.api.ViewUpdateResult;
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

    public ViewUpdateResult toApiVersion() {
        ViewUpdateResult.Type type = ViewUpdateResult.Type.valueOf(this.getType().name());
        return new ViewUpdateResult(type, getGroupId(), client.getUsername());
    }
}
