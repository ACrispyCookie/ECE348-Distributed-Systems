package dev.ioannikas.grouplib.api;

import dev.ioannikas.grouplib.network.serializable.ClientInfo;

import java.util.List;

public class LeaveReply {

    private final int groupId;
    private final LeaveReply.FailReason reason;

    public LeaveReply(int groupId, LeaveReply.FailReason reason) {
        this.groupId = groupId;
        this.reason = reason;
    }

    public int getGroupId() {
        return groupId;
    }

    public LeaveReply.FailReason getReason() {
        return reason;
    }

    public enum FailReason {
        NONE,
        UNKNOWN
    }
}
