package dev.ioannikas.group.library.serializable.requests;

import dev.ioannikas.group.library.serializable.PacketUDP;

import java.nio.ByteBuffer;

public class GroupDataRequest extends PacketUDP {

    private final int groupId;
    private int seqNumber;

    public GroupDataRequest(int groupId, int seqNumber) {
        this.groupId = groupId;
        this.seqNumber = seqNumber;
    }

    public int getGroupId() {
        return groupId;
    }

    public int getSeqNumber() {
        return seqNumber;
    }

    public static PacketUDP deserialize(ByteBuffer buffer) {
        return null;
    }

    public static void serialize(GroupDataRequest packet, ByteBuffer buffer) {
        buffer.putInt(packet.getGroupId());
        buffer.putInt(packet.getSeqNumber());

//        switch (packet.getType()) {
//            case JOIN -> GMSJoinRequest.serialize((GMSJoinRequest) packet, buffer);
//            case LEAVE -> GMSLeaveRequest.serialize((GMSLeaveRequest) packet, buffer);
//        }
    }
}
