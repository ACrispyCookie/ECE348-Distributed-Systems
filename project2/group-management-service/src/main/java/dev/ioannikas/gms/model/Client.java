package dev.ioannikas.gms.model;

import dev.ioannikas.gms.utility.AtomicIdGenerator;
import dev.ioannikas.gms.utility.AtomicIndex;

import java.net.Socket;

public class Client {
    private final int id;
    private int groups;
    private final Socket socket;

    private static final AtomicIndex<Integer, Client> index = new AtomicIndex<>(Client::getId);
    private static final AtomicIdGenerator idGen = new AtomicIdGenerator();

    public Client(Socket socket) {
        this.id = idGen.getNext();
        this.groups = 0;
        this.socket = socket;
    }

    public int getId() {
        return id;
    }

    public Socket getSocket() {
        return socket;
    }

    public int getGroups() {
        return groups;
    }

    public void addGroups(int amount) {
        this.groups += amount;
    }

    public static Client getClient(int id) {
        return index.get(id);
    }

    public static void addClient(Client client) {
        index.add(client);
    }
}
