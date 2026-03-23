package dev.ioannikas.grouplib.managers;

import dev.ioannikas.chatapp.utility.Printer;
import dev.ioannikas.grouplib.GroupAPI;
import dev.ioannikas.grouplib.network.serializable.group.PacketUDP;
import dev.ioannikas.grouplib.network.serializable.gms.ConnectionTCP;
import dev.ioannikas.grouplib.network.serializable.group.ConnectionUDP;
import dev.ioannikas.grouplib.network.serializable.gms.PacketTCP;
import dev.ioannikas.grouplib.network.serializable.gms.replies.GMSReply;
import dev.ioannikas.grouplib.network.serializable.group.requests.NormalRequest;

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

    private void tcpReceiver() {
        while (!isShuttingDown()) {
            try {
                GMSReply incomingObject = (GMSReply) gmsConnection.nextObject();

                if (incomingObject.getType() != GMSReply.ReplyType.UPDATE)
                    getAPI().getGmsManager().handleReceivedPacket(incomingObject);
                else if (incomingObject.getType() == GMSReply.ReplyType.UPDATE)
                    getAPI().getMessagingManager().handleReceivedPacket(new NormalRequest(incomingObject.getGroupId(), incomingObject));
                else
                    Printer.printDebug("[NetworkManager] Received invalid packet from GMS.");
            } catch (Exception e) {
                if (!isShuttingDown()) {
                    Printer.printErr("[NetworkManager] Error communicating with GMS.");
                    e.printStackTrace();
                }
                return;
            }
        }
    }

    private void udpReceiver() {
        while (!isShuttingDown()) {
            try {
                PacketUDP incomingPacket = (PacketUDP) normalConnection.nextObject();

                getAPI().getMessagingManager().handleReceivedPacket(incomingPacket);
            } catch (Exception e) {
                if (!isShuttingDown()) {
                    Printer.printErr("[NetworkManager] Error receiving packet from group.");
                    e.printStackTrace();
                }
                return;
            }
        }
    }

    public void sendGms(PacketTCP packet) throws IOException {
        gmsConnection.sendPacket(packet);
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
            Printer.printErr("[NetworkManager] Could not determine local IP address!");
            return "127.0.0.1";
        }
    }

    @Override
    protected void onShutdown() {
        try {
            gmsConnection.close();
            normalConnection.close();
        } catch (IOException e) {
            Printer.printErr("[NetworkManager] Could not close sockets!");
        }
    }

    @Override
    protected List<Thread> getThreads() {
        Thread tcpReceiverThread = new Thread(this::tcpReceiver, "networkmanager-tcp-receiver");
        Thread udpReceiverThread = new Thread(this::udpReceiver, "networkmanager-udp-receiver");

        return List.of(tcpReceiverThread, udpReceiverThread);
    }
}
