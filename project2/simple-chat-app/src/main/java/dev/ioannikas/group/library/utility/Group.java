package dev.ioannikas.group.library.utility;

import dev.ioannikas.group.gms.serializable.ClientInfo;

import java.util.List;

public class Group {

    private final int groupId;
    private final List<ClientInfo> clients;

    public Group(int groupId, List<ClientInfo> clients) {
        this.groupId = groupId;
        this.clients = clients;
    }

    public int getGroupId() {
        return groupId;
    }

    public List<ClientInfo> getClients() {
        return clients;
    }
}
