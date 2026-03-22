package dev.ioannikas.grouplib.api;

import java.util.List;

public class JoinLeaveResult {
    private final Status status;
    private final int groupId;
    private final FailReason reason;
    private final List<String> clients;

    public JoinLeaveResult(Status status, int groupId, FailReason reason, List<String> clients) {
        this.status = status;
        this.groupId = groupId;
        this.reason = reason;
        this.clients = clients;
    }

    public Status getStatus() {
        return status;
    }

    public int getGroupId() {
        return groupId;
    }

    public FailReason getReason() {
        return reason;
    }

    public List<String> getClients() {
        return clients;
    }

    public enum FailReason {
        USERNAME_TAKEN
    }

    public enum Status {
        SUCCESS,
        ERROR
    }
}
