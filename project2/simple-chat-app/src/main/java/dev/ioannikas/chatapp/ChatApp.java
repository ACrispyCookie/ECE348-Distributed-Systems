package dev.ioannikas.chatapp;

import dev.ioannikas.chatapp.managers.ChatManager;
import dev.ioannikas.chatapp.managers.InputManager;
import dev.ioannikas.chatapp.managers.MessageManager;
import dev.ioannikas.chatapp.utility.Printer;
import dev.ioannikas.grouplib.GroupAPI;

import java.util.concurrent.CountDownLatch;

public class ChatApp {

    private static GroupAPI groupAPI;
    private static ChatManager chatManager;
    private static InputManager inputManager;
    private static MessageManager messageManager;
    private static final CountDownLatch shutdown = new CountDownLatch(1);
    private static boolean shuttingDown = false;
    
    public static void main(String[] args) {
        groupAPI = new GroupAPI();
        groupAPI.init("10.0.0.8", 5000);

        chatManager  = new ChatManager();
        inputManager = new InputManager();
        messageManager = new MessageManager();

        Printer.printInfo("Client started! Start by typing a command!");

        while (!shuttingDown) {
            try {
                shutdown.await();
                shuttingDown = true;
                shutdown();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static GroupAPI getGroupAPI() {
        return groupAPI;
    }

    public static ChatManager getChatManager() {
        return chatManager;
    }

    public static InputManager getInputManager() {
        return inputManager;
    }

    public static MessageManager getMessageManager() {
        return messageManager;
    }

    public static void startShutdown() {
        shutdown.countDown();
    }

    private static void shutdown() {
        Printer.printInfo("Shutting down...");
        inputManager.interrupt();
        messageManager.interrupt();
        groupAPI.shutdown();
    }

    public static boolean isShuttingDown() {
        return shuttingDown;
    }
}
