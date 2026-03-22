package dev.ioannikas.chatapp.managers;

import dev.ioannikas.chatapp.model.GroupChat;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class ChatManager {

    private final Map<Integer, GroupChat> joinedGroups = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public void memberLeft(int groupId, String member) {
        lock.lock();
        try {
            GroupChat chat = joinedGroups.get(groupId);
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
            GroupChat chat = joinedGroups.get(groupId);
            if (chat == null)
                return;

            chat.getMembers().add(member);
        } finally {
            lock.unlock();
        }
    }

    public void joinChat(GroupChat chat) {
        lock.lock();
        try {
            joinedGroups.put(chat.getId(), chat);
        } finally {
            lock.unlock();
        }
    }

    public void leaveChat(int groupId) {
        lock.lock();
        try {
            joinedGroups.remove(groupId);
        } finally {
            lock.unlock();
        }
    }

    public String getUsername(int groupId) {
        lock.lock();
        try {
            GroupChat chat = joinedGroups.get(groupId);
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
            GroupChat chat = joinedGroups.get(groupId);
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
            GroupChat chat = joinedGroups.get(groupId);
            if (chat == null)
                return null;

            return new LinkedList<>(chat.getMembers());
        } finally {
            lock.unlock();
        }
    }

    public boolean exists(int groupId) {
        lock.lock();
        try {
            return joinedGroups.containsKey(groupId);
        } finally {
            lock.unlock();
        }
    }

    public int getByName(String name) {
        lock.lock();
        try {
            for (GroupChat chat : joinedGroups.values()) {
                if (chat.getName().equals(name))
                    return chat.getId();
            }

            return -1;
        } finally {
            lock.unlock();
        }
    }

    public void cleanUp() {

    }
}
