package dev.ioannikas.gms;

import dev.ioannikas.gms.thread.Receiver;
import dev.ioannikas.gms.utility.Printer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class GMS {

    private static final int DEFAULT_PORT = 5000;
    private static boolean shuttingDown = false;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                Printer.printErr("Invalid port: " + args[0] + ", using default " + DEFAULT_PORT);
            }
        }

        Printer.printInfo("GMS starting...");

        try (ServerSocket serverSocket = new ServerSocket(port)) {

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Printer.printInfo("GMS shutting down...");
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    Printer.printErr("Error closing server socket: " + e.getMessage());
                }
                onShutdown();
            }));

            while (!shuttingDown) {
                Printer.printInfo("Waiting for connections on port " + port + "...");
                Socket socket = serverSocket.accept();

                Thread thread = new Receiver(socket);
                thread.start();
            }
        } catch (IOException e) {
            Printer.printErr("GMS failed: " + e.getMessage());
        }
    }

    private static void onShutdown() {
        shuttingDown = true;
        Receiver.onShutdown();
    }
}