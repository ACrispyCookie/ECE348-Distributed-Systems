package dev.ioannikas.chatapp.managers;

import dev.ioannikas.chatapp.ChatApp;
import dev.ioannikas.chatapp.model.Command;
import dev.ioannikas.chatapp.model.Message;
import dev.ioannikas.chatapp.utility.Printer;
import dev.ioannikas.grouplib.api.JoinLeaveResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Scanner;

public class InputManager extends Thread {

    private int selected = -1;

    public InputManager() {
        this.setName("simplechatapp-input-manager");
        this.start();
    }

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);

        while (!ChatApp.isShuttingDown() && scanner.hasNext()) {
            String line = scanner.nextLine();
            Command command = Command.parse(line);

            if (command != null) {
                handleCommand(command);
                continue;
            } else if (line.startsWith("/")) {
                Printer.printErr("Invalid command!");
                continue;
            } else if (selected == -1) {
                Printer.printErr("Select a chat before sending!");
                continue;
            }

            Message message = new Message(ChatApp.getChatManager().getUsername(selected), LocalDateTime.now(), line);
            ChatApp.getGroupAPI().send(selected, message);
        }
    }

    private void handleCommand(Command command) {
        switch (command.getType()) {
            case JOIN -> handleJoin(command);
            case LEAVE -> handleLeave(command);
            case SELECT -> handleSelect(command);
            case INFO -> handleInfo();
            case EXIT -> handleExit();
        }
    }

    private void handleJoin(Command command) {
        if (command.getArgs().length != 2) {
            Printer.printErr("Invalid usage: /join <group name> <username>");
            return;
        }

        String groupName = command.getArgs()[0];
        String username = command.getArgs()[1];

        JoinLeaveResult reply = ChatApp.getGroupAPI().join(groupName, username);
        if (reply.getStatus() == JoinLeaveResult.Status.ERROR) {
            if (reply.getReason() == JoinLeaveResult.FailReason.USERNAME_TAKEN)
                Printer.printErr("This username is taken in this group!");
            else
                Printer.printErr("Error while joining group!");
            return;
        }

        ChatApp.getChatManager().joinChat(reply.getGroupId(), groupName, username, reply.getClients());
        Printer.printInfo("You have joined group " + groupName + "!");
    }

    private void handleLeave(Command command) {
        if (command.getArgs().length != 1) {
            Printer.printErr("Invalid usage: /leave <group ID>");
            return;
        }

        int groupId;
        try {
            groupId = Integer.parseInt(command.getArgs()[0]);
        } catch (Exception e) {
            Printer.printErr("Please enter a valid number!");
            return;
        }

        JoinLeaveResult reply = ChatApp.getGroupAPI().leave(groupId);
        if (reply.getStatus() == JoinLeaveResult.Status.ERROR) {
            Printer.printErr("Error while leaving a group!");
            return;
        }

        ChatApp.getChatManager().leaveChat(groupId);
        Printer.printInfo("You have left group " + groupId + "!");
    }

    private void handleSelect(Command command) {
        if (command.getArgs().length != 1) {
            Printer.printErr("Invalid usage: /select <group name/group ID>");
            return;
        }

        int groupId;
        try {
            groupId = Integer.parseInt(command.getArgs()[0]);
        } catch (Exception ignored) {
            groupId = ChatApp.getChatManager().getByName(command.getArgs()[0]);
        }

        if (groupId == -1) {
            Printer.printErr("Group not found!");
            return;
        }

        selected = groupId;
        Printer.printInfo("You have selected group " + ChatApp.getChatManager().getName(groupId) + "!");
    }

    private void handleInfo() {
        if (selected == -1) {
            Printer.printErr("You must select a chat to print its info!");
            return;
        }

        StringBuilder builder = new StringBuilder("Chat info:");
        builder.append("\n");
        builder.append("Group name: ").append(ChatApp.getChatManager().getName(selected)).append("\n");
        builder.append("Your username: ").append(ChatApp.getChatManager().getUsername(selected)).append("\n");

        List<String> members = ChatApp.getChatManager().getMembers(selected);
        builder.append("Members: ").append(members.size()).append("\n");

        for (String s : members) {
            builder.append("        - ").append(s).append("\n");
        }

        Printer.printInfo(builder.toString());
    }

    private void handleExit() {
        ChatApp.getChatManager().cleanUp();

        ChatApp.startShutdown();
    }
}
