package dev.ioannikas.grouplib.network.serializable.group;

import java.io.Serial;
import java.io.Serializable;

public class PacketUDP implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;
    private static int currentSeqNumber = 0;
    private final int groupId;
    private int seqNumber;

    public PacketUDP(int groupId) {
        this.groupId = groupId;
        this.seqNumber = -1;
    }

    public int getGroupId() {
        return groupId;
    }

    public int getSeqNumber() {
        return seqNumber;
    }

    public void setNextSeqNumber() {
        seqNumber = ++currentSeqNumber;
    }

    public static int getCurrentSeqNumber() {
        return currentSeqNumber;
    }
}
