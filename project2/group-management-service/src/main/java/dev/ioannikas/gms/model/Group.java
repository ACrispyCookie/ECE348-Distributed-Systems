package dev.ioannikas.gms.model;

import dev.ioannikas.gms.utility.AtomicIdGenerator;
import dev.ioannikas.gms.utility.AtomicIndex;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Group {
    private final int id;
    private final String name;
    private final Set<Client> clients;
    private final ReadWriteLock clientsLock = new ReentrantReadWriteLock();

    private static final AtomicIndex<Integer, Group> index = new AtomicIndex<>(Group::getId);
    private static final AtomicIdGenerator idGen = new AtomicIdGenerator();

    public Group(String name) {
        this.name = name;
        this.id = idGen.getNext();
        this.clients = new HashSet<>();
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean containsClient(Client client) {
        clientsLock.readLock().lock();
        try {
            return clients.contains(client);
        } finally {
            clientsLock.readLock().unlock();
        }
    }

    public void addClient(Client client) {
        clientsLock.writeLock().lock();
        try {
            clients.add(client);
        } finally {
            clientsLock.writeLock().unlock();
        }
    }

    public void removeClient(Client client) {
        clientsLock.writeLock().lock();
        try {
            clients.remove(client);
        } finally {
            clientsLock.writeLock().unlock();
        }
    }

    public static Group getGroup(int id) {
        return index.get(id);
    }

    public static void addGroup(Group group) {
        index.add(group);
    }
}
