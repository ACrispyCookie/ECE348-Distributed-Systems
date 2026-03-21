package dev.ioannikas.chatapp.model;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class GroupChat {

    private final int id;
    private final String name;
    private final BlockingQueue<Message> received = new LinkedBlockingQueue<>();

    public GroupChat(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BlockingQueue<Message> getReceived() {
        return received;
    }
}
