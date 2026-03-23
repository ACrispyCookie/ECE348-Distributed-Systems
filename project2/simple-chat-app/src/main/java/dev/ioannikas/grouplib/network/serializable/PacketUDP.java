package dev.ioannikas.grouplib.network.serializable;

public class PacketUDP {
    private final int groupId;
    private final Object object;

    public PacketUDP(int groupId, Object object) {
        this.groupId = groupId;
        this.object = object;
    }

    public int getGroupId() {
        return groupId;
    }

    public Object getObject() {
        return object;
    }
}
