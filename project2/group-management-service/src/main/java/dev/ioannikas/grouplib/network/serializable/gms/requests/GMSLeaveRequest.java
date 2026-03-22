package dev.ioannikas.grouplib.network.serializable.gms.requests;

import java.io.Serial;

public class GMSLeaveRequest extends GMSRequest {

    @Serial
    private static final long serialVersionUID = 2L;
    private final int groupId;

    public GMSLeaveRequest(String username, int futureId, int groupId) {
        super(RequestType.LEAVE, username, futureId);
        this.groupId = groupId;
    }

    public int getGroupId() {
        return groupId;
    }
}
