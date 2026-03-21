package dev.ioannikas.chatapp;

import dev.ioannikas.chatapp.managers.CommandManager;
import dev.ioannikas.chatapp.managers.InputManager;
import dev.ioannikas.chatapp.managers.OutputManager;
import dev.ioannikas.chatapp.model.GroupChat;

public class ChatApp {

    private static GroupChat group;
    private static InputManager inputManager;
    private static OutputManager outputManager;
    private static CommandManager commandManager;
    
    public static void main(String[] args) {
        //GMS initialize

        inputManager = new InputManager();
        outputManager = new OutputManager();
        commandManager = new CommandManager();
        System.out.println("[SERVER]");
    }

    public static GroupChat getGroup() {
        return group;
    }
}
