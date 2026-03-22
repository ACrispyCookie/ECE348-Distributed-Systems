package dev.ioannikas.grouplib.network.serializable;

import java.io.Serial;
import java.io.Serializable;

public class ClientInfo implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;
    private final String username;
    private final String ipAddress;
    private final int port;

    public ClientInfo(String username, String ipAddress, int port) {
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

    @Override
    public String toString() {
        return "Username: " + username + " IP: " + ipAddress + " Port: " + port;
    }
}
