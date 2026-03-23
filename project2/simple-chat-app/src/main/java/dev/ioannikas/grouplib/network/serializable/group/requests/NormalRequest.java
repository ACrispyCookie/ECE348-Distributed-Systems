package dev.ioannikas.grouplib.network.serializable.group.requests;

import dev.ioannikas.grouplib.network.serializable.group.PacketUDP;

import java.io.Serial;

public class NormalRequest extends PacketUDP {

    @Serial
    private static final long serialVersionUID = 2L;
    private final Object data;

    public NormalRequest(int groupId, Object data) {
        super(groupId);
        this.data = data;
    }

    public Object getData() {
        return data;
    }
}
