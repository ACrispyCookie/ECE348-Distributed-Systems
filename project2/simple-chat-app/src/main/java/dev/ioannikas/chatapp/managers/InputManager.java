package dev.ioannikas.chatapp.managers;

import dev.ioannikas.chatapp.ChatApp;
import dev.ioannikas.chatapp.model.Command;
import dev.ioannikas.chatapp.model.ChatMessage;
import dev.ioannikas.chatapp.utility.Printer;
import dev.ioannikas.group.api.JoinReply;
import dev.ioannikas.group.api.LeaveReply;

import java.time.LocalDateTime;
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
            ChatApp.getGroupAPI().send(selected, chatMessage);
        }
    }

    private Command.Type handleCommand(Command command) {
        switch (command.getType()) {
            case JOIN -> handleJoin(command);
            case LEAVE -> handleLeave(command);
            case SELECT -> handleSelect(command);
            case INFO -> handleInfo();
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

        JoinReply reply = ChatApp.getGroupAPI().join(groupName, username);
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

        LeaveReply reply = ChatApp.getGroupAPI().leave(groupId);
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

    private void handleExit() {
        ChatApp.getChatManager().cleanUp();
        ChatApp.shutdown();
    }
}
