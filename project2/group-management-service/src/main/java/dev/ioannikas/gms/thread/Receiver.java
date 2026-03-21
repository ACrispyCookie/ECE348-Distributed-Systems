package dev.ioannikas.gms.thread;

import dev.ioannikas.gms.model.Client;
import dev.ioannikas.gms.utility.Printer;

import java.net.Socket;

public class Receiver extends Thread {

    private final Client client;

    public Receiver(Socket socket) {
        client = new Client(socket);
        Client.addClient(client);
        setName("client-" + client.getId());
    }

    public void run() {
        Printer.printClient("Client " + client.getId() + " connected from " + client.getSocket().getInetAddress().getHostAddress());
    }
}
