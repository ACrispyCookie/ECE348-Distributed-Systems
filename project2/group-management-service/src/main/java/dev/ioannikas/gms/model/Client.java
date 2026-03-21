package dev.ioannikas.gms.model;

import java.io.Serializable;

public class Client implements Serializable {
    private final String username;
    private final String ipAddress;
    private final int port;

    public Client(String username, String ipAddress, int port) {
        this.username = username;
        this.ipAddress = ipAddress;
        this.port = port;
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
}
