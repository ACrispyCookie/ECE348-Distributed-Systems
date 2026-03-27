package dev.ioannikas.group.gms.utility;

import dev.ioannikas.group.gms.serializable.ClientInfo;

import java.util.*;
import java.util.function.Consumer;

public class GroupManager {

    private final Map<Integer, Group> index = new HashMap<>();
    private int nextId = 0;

    public int findOrCreateGroup(String name) {
        for (Group group : index.values()) {
            if (group.getName().equals(name))
                return group.getId();
        }

        Group group = new Group(name, ++nextId);
        index.put(nextId, group);
        return group.getId();
    }

    public ReturnCode addToGroup(int groupId, ClientInfo client) {
        Group group = index.get(groupId);
        if (group == null)
            return ReturnCode.GROUP_NOT_FOUND;

        boolean added = group.addClient(client);
        return added ? ReturnCode.SUCCESS : ReturnCode.CLIENT_ALREADY_EXISTS;
    }

    public ReturnCode removeFromGroup(int groupId, String username) {
        Group group = index.get(groupId);
        if (group == null)
            return ReturnCode.GROUP_NOT_FOUND;

        ClientInfo previous = group.removeClient(username);
        return previous != null ? ReturnCode.SUCCESS : ReturnCode.CLIENT_NOT_FOUND;
    }

    public List<ClientInfo> getClients(int groupId) {
        Group group = index.get(groupId);
        if (group == null)
            return null;

        return new LinkedList<>(group.getClients());
    }

    public void forEachClient(int groupId, Consumer<ClientInfo> consumer) {
        Group group = index.get(groupId);
        if (group == null)
            return;

        for (ClientInfo client : group.getClients()) {
            consumer.accept(client);
        }
    }

    public int getClientCount() {
        int users = 0;
        for (Group g : index.values())
            users += g.getClients().size();
        return users;
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
        private final List<ClientInfo> clients;

        public Group(String name, int id) {
            this.name = name;
            this.id = id;
            this.clients = new LinkedList<>();
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public boolean addClient(ClientInfo client) {
            if (clients.stream().anyMatch(c -> c.getUsername().equals(client.getUsername())))
                return false;
            clients.add(client);
            return true;
        }

        public ClientInfo removeClient(String username) {
            ClientInfo info = null;
            for (ClientInfo client : clients) {
                if (client.getUsername().equals(username)) {
                    info = client;
                }
            }

            clients.remove(info);
            return info;
        }

        public List<ClientInfo> getClients() {
            return clients;
        }
    }
}
