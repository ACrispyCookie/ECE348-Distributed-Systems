package dev.ioannikas.chatapp.model;

import java.util.List;

public class GroupChat {

    private final int id;
    private final String name;
    private final String username;
    private final List<String> members;

    public GroupChat(int id, String name, String username, List<String> members) {
        this.id = id;
        this.name = name;
        this.username = username;
        this.members = members;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUsername() {
        return username;
    }

    public List<String> getMembers() {
        return members;
    }
}
