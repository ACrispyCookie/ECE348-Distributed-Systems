package dev.ioannikas.group.gms;

import dev.ioannikas.group.gms.utility.Printer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class GMS {

    private static final int DEFAULT_PORT = 10100;
    private static boolean shuttingDown = false;
    private static GroupManager groupManager;
    private static ServerSocket serverSocket;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                Printer.printErr("Invalid port: " + args[0] + ", using default " + DEFAULT_PORT);
            }
        }

        groupManager = new GroupManager();
        Printer.printInfo("GMS starting...");

        startAccepting(port);
    }

    private static void startAccepting(int port) {
        try {
            serverSocket = new ServerSocket(port);
            Runtime.getRuntime().addShutdownHook(new Thread(GMS::shutdown));

            Printer.printInfo("Waiting for connections on port " + port + "...");
            while (!shuttingDown) {
                Socket socket = serverSocket.accept();

                Thread thread = new ClientThread(groupManager, socket);
                thread.start();
            }
        } catch (IOException e) {
            Printer.printErr("GMS failed: " + e.getMessage());
        }
    }

    public static void shutdown() {
        Printer.printInfo("GMS shutting down...");
        shuttingDown = true;
        ClientThread.onShutdown();
        try {
            serverSocket.close();
        } catch (IOException e) {
            Printer.printErr("Error closing server socket: " + e.getMessage());
        }
    }

    public static int connectedClients() {
        return groupManager.getClientCount();
    }
}