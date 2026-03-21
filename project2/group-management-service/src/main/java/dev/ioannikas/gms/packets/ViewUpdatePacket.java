package dev.ioannikas.gms.packets;

import dev.ioannikas.gms.model.Client;

import java.io.Serializable;

public class ViewUpdatePacket implements Serializable {

    private final UpdateType type;
    private final int groupId;
    private final Client client;

    public ViewUpdatePacket(UpdateType type, int groupId, Client client) {
        this.type = type;
        this.groupId = groupId;
        this.client = client;
    }

    public UpdateType getType() {
        return type;
    }

    public int getGroupId() {
        return groupId;
    }

    public Client getClient() {
        return client;
    }

    public enum UpdateType {
        JOIN,
        LEAVE
    }
}
