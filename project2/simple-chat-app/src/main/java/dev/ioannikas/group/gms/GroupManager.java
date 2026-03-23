package dev.ioannikas.group.gms;

import dev.ioannikas.group.gms.serializable.ClientInfo;

import java.util.*;
import java.util.function.Consumer;

public class GroupManager {

    private final Map<Integer, Group> index = new HashMap<>();
    private int nextId = 0;

    public synchronized int findOrCreateGroup(String name) {
        for (Group group : index.values()) {
            if (group.getName().equals(name))
                return group.getId();
        }

        Group group = new Group(name, ++nextId);
        index.put(nextId, group);
        return group.getId();
    }

    public synchronized ReturnCode addToGroup(int groupId, ClientInfo client) {
        Group group = index.get(groupId);
        if (group == null)
            return ReturnCode.GROUP_NOT_FOUND;

        ClientInfo previous = group.addClient(client);
        return previous == null ? ReturnCode.SUCCESS : ReturnCode.CLIENT_ALREADY_EXISTS;
    }

    public synchronized ReturnCode removeFromGroup(int groupId, String username) {
        Group group = index.get(groupId);
        if (group == null)
            return ReturnCode.GROUP_NOT_FOUND;

        ClientInfo previous = group.removeClient(username);
        return previous != null ? ReturnCode.SUCCESS : ReturnCode.CLIENT_NOT_FOUND;
    }

    public synchronized String getGroupName(int groupId) {
        Group group = index.get(groupId);
        if (group == null)
            return null;

        return group.getName();
    }

    public synchronized List<ClientInfo> getClients(int groupId) {
        Group group = index.get(groupId);
        if (group == null)
            return null;

        return new LinkedList<>(group.getClients());
    }

    public synchronized void forEachClient(int groupId, Consumer<ClientInfo> consumer) {
        Group group = index.get(groupId);
        if (group == null)
            return;

        for (ClientInfo client : group.getClients()) {
            consumer.accept(client);
        }
    }

    public enum ReturnCode {
        SUCCESS,
        GROUP_NOT_FOUND,
        CLIENT_ALREADY_EXISTS,
        CLIENT_NOT_FOUND
    }

    private static class Group {
        private final int id;
        private final String name;
        private final Map<String, ClientInfo> clients;

        public Group(String name, int id) {
            this.name = name;
            this.id = id;
            this.clients = new HashMap<>();
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public ClientInfo addClient(ClientInfo client) {
            return clients.putIfAbsent(client.getUsername(), client);
        }

        public ClientInfo removeClient(String username) {
            return clients.remove(username);
        }

        public Collection<ClientInfo> getClients() {
            return clients.values();
        }
    }
}
