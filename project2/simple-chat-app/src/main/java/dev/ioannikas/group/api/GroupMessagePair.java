package dev.ioannikas.group.api;

public class GroupMessagePair {

    private final int groupId;
    private final Object message;

    public GroupMessagePair(int groupId, Object message) {
        this.groupId = groupId;
        this.message = message;
    }

    public int getGroupId() {
        return groupId;
    }

    public Object getMessage() {
        return message;
    }
}
