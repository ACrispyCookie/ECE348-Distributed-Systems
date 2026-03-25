package dev.ioannikas.chatapp.managers;

import dev.ioannikas.chatapp.model.ChatMessage;
import dev.ioannikas.chatapp.utility.Printer;
import dev.ioannikas.group.GroupAPI;

import java.util.*;

public class ChatManager {

    private final Map<Integer, GroupChat> chats = new HashMap<>();

    public synchronized void memberLeft(int groupId, String member) {
        GroupChat chat = chats.get(groupId);
        if (chat == null)
            return;

        chat.getMembers().remove(member);
        Printer.printMessage(chat.getName(), groupId, member + " has left the chat!");
    }

    public synchronized void memberJoined(int groupId, String member) {
        GroupChat chat = chats.get(groupId);
        if (chat == null)
            return;

        chat.getMembers().add(member);
        Printer.printMessage(chat.getName(), groupId, member + " has joined the chat!");
    }

    public synchronized void joinChat(int id, String name, String username, List<String> members) {
        GroupChat chat = new GroupChat(id, name, username, new LinkedList<>(members));
        chats.put(id, chat);
    }

    public synchronized void leaveChat(int groupId) {
        chats.remove(groupId);
    }

    public synchronized int getByIdOrName(String idOrName) {
        int groupId;
        try {
            groupId = Integer.parseInt(idOrName);
        } catch (Exception ignored) {
            groupId = -1;
            for (GroupChat chat : chats.values())
                if (chat.getName().equals(idOrName)) {
                    groupId = chat.getId();
                    break;
                }
        }

        if (groupId == -1 || chats.get(groupId) == null)
            return -1;

        return groupId;
    }

    public synchronized List<Integer> getGroups() {
        return chats.keySet().stream().toList();
    }

    public synchronized String getUsername(int groupId) {
        GroupChat chat = chats.get(groupId);
        if (chat == null)
            return null;

        return chat.getUsername();
    }

    public synchronized void printChatMessage(int groupId, ChatMessage message) {
        GroupChat chat = chats.get(groupId);
        if (chat == null) {
            Printer.printErr("[ChatApp] Error displaying orphaned message!");
            return;
        }

        Printer.printMessage(chat.getName(), groupId, "At " + message.getDate() + " " + message.getAuthor() + " said: " + message.getMessage());
    }

    public synchronized void printChatInfo(int groupId) {
        GroupChat chat = chats.get(groupId);
        if (chat == null) {
            Printer.printErr("[ChatApp] Group not found!");
            return;
        }

        StringBuilder builder = new StringBuilder("[ChatApp] --- Chat info ---");
        builder.append("\n");
        builder.append("[ChatApp] Group ID: ").append(chat.getId()).append("\n");
        builder.append("[ChatApp] Group name: ").append(chat.getName()).append("\n");
        builder.append("[ChatApp] Your username: ").append(chat.getUsername()).append("\n");

        List<String> members = chat.getMembers();
        builder.append("[ChatApp] Members: ").append(members.size()).append("\n");

        for (String s : members) {
            builder.append("            - ").append(s).append("\n");
        }

        Printer.printInfo(builder.toString());
    }

    public synchronized void cleanUp() {
        for (GroupChat chat : chats.values())
            GroupAPI.leave(chat.getId());
        chats.clear();
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
