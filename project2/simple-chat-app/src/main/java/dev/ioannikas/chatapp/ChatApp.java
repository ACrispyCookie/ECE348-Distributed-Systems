package dev.ioannikas.chatapp;

import dev.ioannikas.chatapp.managers.ChatManager;
import dev.ioannikas.chatapp.managers.InputManager;
import dev.ioannikas.chatapp.managers.MessageReceiver;
import dev.ioannikas.chatapp.utility.Printer;
import dev.ioannikas.group.GroupAPI;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class ChatApp {

    private static ChatManager chatManager;
    private static MessageReceiver receiver;
    private static boolean shuttingDown = false;
    
    public static void main(String[] args) {
        AppConfig config = parseArguments(args);

        try {
            GroupAPI.init(config.getGmsIp(), config.getGmsPort(), config.getAppPort());
        } catch (IOException e) {
            Printer.printErr("[ChatApp] Error initializing GMS! Is the GMS running?");
            e.printStackTrace();
            return;
        }
        Printer.printInfo("[ChatApp] GMS successfully initialized!");
        Printer.printInfo("[ChatApp] Client started! Start by typing a command!");

        chatManager  = new ChatManager();
        receiver = new MessageReceiver();
        receiver.start();
        new InputManager().runInputManager();
    }

    public static ChatManager getChatManager() {
        return chatManager;
    }

    public static MessageReceiver getReceiver() {
        return receiver;
    }

    public static void shutdown() {
        Printer.printInfo("[ChatApp] Shutting down...");
        shuttingDown = true;
        receiver.shutdown();
        GroupAPI.shutdown();
    }

    public static boolean isShuttingDown() {
        return shuttingDown;
    }

    private static class AppConfig {
        private final String gmsIp;
        private final int gmsPort;
        private final int appPort;

        public AppConfig(String gmsIp, int gmsPort, int appPort) {
            this.gmsIp = gmsIp;
            this.gmsPort = gmsPort;
            this.appPort = appPort;
        }

        public String getGmsIp() { return gmsIp; }
        public int getGmsPort() { return gmsPort; }
        public int getAppPort() { return appPort; }
    }

    private static AppConfig parseArguments(String[] args) {
        int defaultAppPort = 0;

        if (args.length == 2 || args.length == 3) {
            try {
                String ip = args[0];
                int port = Integer.parseInt(args[1]);
                int appPort = (args.length == 3) ? Integer.parseInt(args[2]) : defaultAppPort;

                return new AppConfig(ip, port, appPort);
            } catch (NumberFormatException e) {
                Printer.printErr("[ChatApp] Invalid port number provided in arguments. Exiting.");
                System.exit(1);
            }
        } else if (args.length == 1) {
            return parseConfigFile(args[0]);
        }

        printUsage();
        System.exit(1);
        return null;
    }

    private static AppConfig parseConfigFile(String filePath) {
        try {
            String content = Files.readString(Paths.get(filePath));

            String ip = content.split("\"ip\"\\s*:\\s*\"")[1].split("\"")[0];
            String portStr = content.split("\"port\"\\s*:\\s*")[1].split("[,}\\s]")[0];
            int port = Integer.parseInt(portStr.trim());

            int appPort = 0;

            if (content.contains("\"appPort\"")) {
                String appPortStr = content.split("\"appPort\"\\s*:\\s*")[1].split("[,}\\s]")[0];
                appPort = Integer.parseInt(appPortStr.trim());
            }

            return new AppConfig(ip, port, appPort);
        } catch (Exception e) {
            Printer.printErr("[ChatApp] Failed to read or parse config file: " + filePath);
            System.exit(1);
            return null;
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -jar simple-chat-app.jar <GMS_IP> <GMS_PORT> [APP_UDP_PORT]");
        System.out.println("  java -jar simple-chat-app.jar <path_to_config.json>");
    }
}
