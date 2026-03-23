package dev.ioannikas.grouplib.api;

public class ViewUpdateMessage {
    private final int groupId;
    private final UpdateAction action;
    private final String client;

    public ViewUpdateMessage(int groupId, UpdateAction action, String client) {
        this.groupId = groupId;
        this.action = action;
        this.client = client;
    }

    public int getGroupId() {
        return groupId;
    }

    public UpdateAction getAction() {
        return action;
    }

    public String getClient() {
        return client;
    }

    public enum UpdateAction {
        JOINED,
        LEFT
    }
}
