package dev.ioannikas.gms.thread;

import dev.ioannikas.gms.packets.GMSPacket;
import dev.ioannikas.gms.utility.AtomicIdGenerator;
import dev.ioannikas.gms.utility.Printer;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class Receiver extends Thread {

    private static int TIMEOUT = 5;
    private static boolean shuttingDown = false;
    private static AtomicIdGenerator idGen = new AtomicIdGenerator();

    private final Socket socket;
    private final int id;

    public Receiver(Socket socket) {
        this.socket = socket;
        setName("client-" + socket.getInetAddress().getHostAddress());
    }

    public void run() {
        Printer.printDebug("Client " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort() + " connected!");

        try {
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            socket.setSoTimeout(TIMEOUT);

            while (!shuttingDown) {
                GMSPacket packet = (GMSPacket) in.readObject();
                switch (packet.getType()) {
                    case JOIN -> handleJoin(packet);
                    case LEAVE -> handleLeave(packet);
                    default -> {}
                }
            }
        } catch (SocketTimeoutException e) {
            handleFailure();
        } catch (IOException | ClassNotFoundException e) {
            Printer.printErr("Client " + socket.getInetAddress().getHostAddress() + ":" + socket.getPort() + " encountered error: " + e.getMessage());
        }
    }

    private void handleJoin(GMSPacket packet) {

    }

    private void handleLeave(GMSPacket packet) {

    }

    private void handleFailure(GMSPacket packet) {

    }

    public static void setTimeout(int seconds) {
        TIMEOUT = seconds;
    }

    public static void onShutdown() {
        shuttingDown = true;
    }
}
