package dev.ioannikas.grouplib.api;

public class ViewUpdateResult {
    private final Type type;
    private final int groupId;
    private final String client;

    public ViewUpdateResult(Type type, int groupId, String client) {
        this.type = type;
        this.groupId = groupId;
        this.client = client;
    }

    public Type getType() {
        return type;
    }

    public int getGroupId() {
        return groupId;
    }

    public String getClient() {
        return client;
    }

    public enum Type {
        JOIN,
        LEAVE
    }
}
