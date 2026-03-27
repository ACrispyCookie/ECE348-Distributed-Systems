package dev.ioannikas.chatapp.managers;

import dev.ioannikas.chatapp.ChatApp;
import dev.ioannikas.chatapp.model.Command;
import dev.ioannikas.chatapp.model.ChatMessage;
import dev.ioannikas.chatapp.utility.Printer;
import dev.ioannikas.group.GroupAPI;
import dev.ioannikas.group.api.JoinReply;
import dev.ioannikas.group.api.LeaveReply;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.Scanner;

public class InputManager {

    private int selected = -1;

    public void runInputManager() {
        Scanner scanner = new Scanner(System.in);

        while (scanner.hasNext()) {
            String line = scanner.nextLine();
            Command command = Command.parse(line);

            if (command != null) {
                if (handleCommand(command) == Command.Type.EXIT)
                    break;
                continue;
            } else if (line.startsWith("/")) {
                Printer.printErr("[ChatApp] Invalid command!");
                continue;
            } else if (selected == -1) {
                Printer.printErr("[ChatApp] Select a chat before sending!");
                continue;
            }

            ChatMessage chatMessage = new ChatMessage(ChatApp.getChatManager().getUsername(selected), LocalDateTime.now(), line);
            if (ChatApp.getApi().send(selected, chatMessage) == -1)
                Printer.printErr("[ChatApp] You are not in this group! (How did we get here?)");
        }
    }

    private Command.Type handleCommand(Command command) {
        switch (command.getType()) {
            case JOIN -> handleJoin(command);
            case LEAVE -> handleLeave(command);
            case SELECT -> handleSelect(command);
            case INFO -> handleInfo();
            case SPAM -> handleSpam(command);
            case EXIT -> handleExit();
        }

        return command.getType();
    }

    private void handleJoin(Command command) {
        if (command.getArgs().length != 2) {
            Printer.printErr("[ChatApp] Invalid usage: /join <group name> <username>");
            return;
        }

        String groupName = command.getArgs()[0];
        String username = command.getArgs()[1];

        int groupId = ChatApp.getChatManager().getByIdOrName(command.getArgs()[0]);
        if (groupId != -1) {
            Printer.printErr("[ChatApp] You are already in this group!");
            return;
        }

        JoinReply reply = ChatApp.getApi().join(groupName, username);
        if (reply.getReason() != JoinReply.FailReason.NONE) {
            if (reply.getReason() == JoinReply.FailReason.USERNAME_TAKEN)
                Printer.printErr("[ChatApp] This username is taken in this group!");
            else
                Printer.printErr("[ChatApp] Error while joining group!");
            return;
        }

        ChatApp.getChatManager().joinChat(reply.getGroupId(), groupName, username, reply.getClients());
        Printer.printInfo("[ChatApp] You have joined group " + groupName + " (ID:" + reply.getGroupId() + ")!");
        ChatApp.getChatManager().printChatInfo(reply.getGroupId());
        if (selected == -1) {
            selected = reply.getGroupId();
            Printer.printInfo("[ChatApp] You have selected group " + groupName + " (ID:" + reply.getGroupId() + ")!");
        }
        ChatApp.getReceiver().interrupt();
    }

    private void handleLeave(Command command) {
        if (command.getArgs().length != 1) {
            Printer.printErr("[ChatApp] Invalid usage: /leave <group name/group ID>");
            return;
        }

        int groupId = ChatApp.getChatManager().getByIdOrName(command.getArgs()[0]);
        if (groupId == -1) {
            Printer.printErr("[ChatApp] Group not found!");
            return;
        }

        LeaveReply reply = ChatApp.getApi().leave(groupId);
        if (reply.getReason() != LeaveReply.FailReason.NONE) {
            Printer.printErr("[ChatApp] Error while leaving a group!");
            return;
        }

        ChatApp.getChatManager().leaveChat(groupId);
        if (selected == groupId)
            selected = -1;
        Printer.printInfo("[ChatApp] You have left group with ID:" + groupId + "!");
        ChatApp.getReceiver().interrupt();
    }

    private void handleSelect(Command command) {
        if (command.getArgs().length != 1) {
            Printer.printErr("[ChatApp] Invalid usage: /select <group name/group ID>");
            return;
        }

        int groupId = ChatApp.getChatManager().getByIdOrName(command.getArgs()[0]);
        if (groupId == -1) {
            Printer.printErr("[ChatApp] Group not found!");
            return;
        }

        selected = groupId;
        Printer.printInfo("[ChatApp] You have selected group with ID:" + groupId + "!");
    }

    private void handleInfo() {
        if (selected == -1) {
            Printer.printErr("[ChatApp] You must select a chat to print its info!");
            return;
        }

        ChatApp.getChatManager().printChatInfo(selected);
    }

    private void handleSpam(Command command) {
        if (command.getArgs().length != 1) {
            Printer.printErr("[ChatApp] Invalid usage: /spam <amount of messages>");
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(command.getArgs()[0]);
            if (amount <= 0)
                amount = -1;
        } catch (Exception e) {
            amount = -1;
        }

        if (amount == -1) {
            Printer.printErr("[ChatApp] Please enter a positive number!");
            return;
        }

        if (selected == -1) {
            Printer.printErr("[ChatApp] You must select a chat to spam!");
            return;
        }

        for (int i = 0; i < amount; i++) {
            ChatMessage chatMessage = new ChatMessage(ChatApp.getChatManager().getUsername(selected), LocalDateTime.now(), randomMessage());
            if (ChatApp.getApi().send(selected, chatMessage) == -1)
                Printer.printErr("[ChatApp] You are not in this group! (How did we get here?)");
        }
    }

    private void handleExit() {
        ChatApp.getChatManager().cleanUp();
        ChatApp.shutdown();
    }

    private static final Random RANDOM = new Random();

    private String randomMessage() {
        int length = RANDOM.nextInt(10) + 5;
        return RANDOM.ints('a', 'z' + 1)
                .limit(length)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
