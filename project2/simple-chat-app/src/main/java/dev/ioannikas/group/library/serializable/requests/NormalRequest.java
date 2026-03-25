package dev.ioannikas.group.library.serializable.requests;

import dev.ioannikas.group.library.serializable.PacketUDP;

import java.io.Serial;

public class NormalRequest extends PacketUDP {

    @Serial
    private static final long serialVersionUID = 2L;
    private final int groupId;
    private final Object data;

    public NormalRequest(int groupId, Object data) {
        this.groupId = groupId;
        this.data = data;
    }

    public int getGroupId() {
        return groupId;
    }

    public Object getData() {
        return data;
    }
}
