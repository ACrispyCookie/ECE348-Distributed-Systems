package dev.ioannikas.chatapp.managers;

import dev.ioannikas.chatapp.ChatApp;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class ChatManager {

    private final Map<Integer, GroupChat> chats = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public void memberLeft(int groupId, String member) {
        lock.lock();
        try {
            GroupChat chat = chats.get(groupId);
            if (chat == null)
                return;

            chat.getMembers().remove(member);
        } finally {
            lock.unlock();
        }

    }

    public void memberJoined(int groupId, String member) {
        lock.lock();
        try {
            GroupChat chat = chats.get(groupId);
            if (chat == null)
                return;

            chat.getMembers().add(member);
        } finally {
            lock.unlock();
        }
    }

    public void joinChat(int id, String name, String username, List<String> members) {
        lock.lock();
        try {
            chats.put(id, new GroupChat(id, name, username, members));
        } finally {
            lock.unlock();
        }
    }

    public void leaveChat(int groupId) {
        lock.lock();
        try {
            chats.remove(groupId);
        } finally {
            lock.unlock();
        }
    }

    public String getUsername(int groupId) {
        lock.lock();
        try {
            GroupChat chat = chats.get(groupId);
            if (chat == null)
                return null;

            return chat.getUsername();
        } finally {
            lock.unlock();
        }
    }

    public String getName(int groupId) {
        lock.lock();
        try {
            GroupChat chat = chats.get(groupId);
            if (chat == null)
                return null;

            return chat.getName();
        } finally {
            lock.unlock();
        }
    }

    public List<String> getMembers(int groupId) {
        lock.lock();
        try {
            GroupChat chat = chats.get(groupId);
            if (chat == null)
                return null;

            return new LinkedList<>(chat.getMembers());
        } finally {
            lock.unlock();
        }
    }

    public int getByName(String name) {
        lock.lock();
        try {
            for (GroupChat chat : chats.values()) {
                if (chat.getName().equals(name))
                    return chat.getId();
            }

            return -1;
        } finally {
            lock.unlock();
        }
    }

    public void cleanUp() {
        lock.lock();
        try {
            for (GroupChat chat : chats.values())
                ChatApp.getGroupAPI().leave(chat.getId());
            chats.clear();
        } finally {
            lock.unlock();
        }
    }

    private static class GroupChat {

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
}
