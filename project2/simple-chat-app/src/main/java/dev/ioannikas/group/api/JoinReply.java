package dev.ioannikas.group.api;

import java.util.List;

public class JoinReply {

    private final int groupId;
    private final JoinReply.FailReason reason;
    private final List<String> clients;

    public JoinReply(int groupId, JoinReply.FailReason reason, List<String> clients) {
        this.groupId = groupId;
        this.reason = reason;
        this.clients = clients;
    }

    public int getGroupId() {
        return groupId;
    }

    public JoinReply.FailReason getReason() {
        return reason;
    }

    public List<String> getClients() {
        return clients;
    }

    public enum FailReason {
        NONE,
        USERNAME_TAKEN,
        UNKNOWN
    }
}
