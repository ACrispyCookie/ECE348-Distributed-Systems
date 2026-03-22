package dev.ioannikas.grouplib.network.serializable.gms;

import dev.ioannikas.gms.network.ReceiverThread;

import java.io.Serial;
import java.io.Serializable;

public class ClientInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;
    private final String username;
    private final String ipAddress;
    private final int port;
    private final transient ReceiverThread manager;

    public ClientInfo(String username, String ipAddress, int port, ReceiverThread manager) {
        this.username = username;
        this.ipAddress = ipAddress;
        this.port = port;
        this.manager = manager;
    }

    public String getUsername() {
        return username;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    public ReceiverThread getManager() {
        return manager;
    }

    @Override
    public String toString() {
        return "Username: " + username + " IP: " + ipAddress + " Port: " + port;
    }
}
