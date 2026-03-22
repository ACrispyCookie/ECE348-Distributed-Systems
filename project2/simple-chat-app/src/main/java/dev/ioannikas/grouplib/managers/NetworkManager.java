package dev.ioannikas.grouplib.managers;

import dev.ioannikas.chatapp.utility.Printer;
import dev.ioannikas.grouplib.GroupAPI;
import dev.ioannikas.grouplib.network.serializable.gms.ConnectionTCP;
import dev.ioannikas.grouplib.network.serializable.ConnectionUDP;

import java.io.IOException;
import java.net.*;
import java.util.List;

public class NetworkManager extends GeneralManager {

    private final ConnectionTCP gmsConnection;
    private final ConnectionUDP normalConnection;
    private final String ipAddress;
    private final int port;

    public NetworkManager(GroupAPI api, String gmsIpAddress, int gmsPort, int appPort) throws IOException {
        super(api);
        this.gmsConnection = new ConnectionTCP(new Socket(gmsIpAddress, gmsPort));
        this.normalConnection = new ConnectionUDP(new DatagramSocket(appPort));
        this.ipAddress = findLocalIp();
        this.port = appPort;

        start();
    }

    private void receiver() {

    }

    public ConnectionTCP getGmsConnection() {
        return gmsConnection;
    }

    public ConnectionUDP getNormalConnection() {
        return normalConnection;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return port;
    }

    private String findLocalIp() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);

            String ip = socket.getLocalAddress().getHostAddress();
            if (ip != null && !ip.equals("0.0.0.0") && !ip.startsWith("127.")) {
                return ip;
            }
        } catch (SocketException | UnknownHostException ignored) {

        }

        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            Printer.printErr("[GroupAPI] CRITICAL: Could not determine local IP address!");
            return "127.0.0.1";
        }
    }

    @Override
    protected void onShutdown() {

    }

    @Override
    protected List<Thread> getThreads() {
        Thread receiver = new Thread(this::receiver);
        receiver.setName("networkmanager-receiver");
        receiver.start();

        return List.of(receiver);
    }
}
