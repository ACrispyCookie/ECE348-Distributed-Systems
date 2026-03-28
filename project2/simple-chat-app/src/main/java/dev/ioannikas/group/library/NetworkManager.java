package dev.ioannikas.group.library;

import dev.ioannikas.chatapp.utility.Printer;
import dev.ioannikas.group.GroupAPI;
import dev.ioannikas.group.gms.serializable.ConnectionTCP;
import dev.ioannikas.group.library.serializable.ConnectionUDP;
import dev.ioannikas.group.library.serializable.PacketUDP;
import dev.ioannikas.group.gms.serializable.PacketTCP;
import dev.ioannikas.group.gms.serializable.replies.GMSReply;
import dev.ioannikas.group.library.serializable.requests.NormalRequest;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class NetworkManager {

    private final GroupAPI api;
    private final String ipAddress;

    private final Selector selector;
    private final ConnectionTCP gmsConnection;
    private final ConnectionUDP normalConnection;
    private final Thread receiver;

    public NetworkManager(GroupAPI api, String gmsIpAddress, int gmsPort, int appPort) throws IOException {
        this.api = api;
        this.ipAddress = findLocalIp();
        this.selector = Selector.open();
        this.normalConnection = new ConnectionUDP(selector, appPort);
        this.gmsConnection = new ConnectionTCP(selector, gmsIpAddress, gmsPort);
        this.receiver = new Thread(this::receiver, "receiver");
        receiver.start();
    }

    private void receiver() {
        ByteBuffer sharedBuffer = ByteBuffer.allocate(2048);

        while (!api.isShuttingDown()) {
            try {
                selector.select();

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();

                while(iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();

                    if (!key.isValid() || !key.isReadable()) {
                        continue;
                    }
                    sharedBuffer.clear();

                    if (key.channel() instanceof SocketChannel client) {
                        client.read(sharedBuffer);
                        sharedBuffer.flip();

                        while (sharedBuffer.hasRemaining())
                            handleGMSPacket(sharedBuffer);
                    } else if (key.channel() instanceof DatagramChannel peer) {
                        InetSocketAddress senderAddr = (InetSocketAddress) peer.receive(sharedBuffer);
                        if (senderAddr != null)
                            sharedBuffer.flip();

                        PacketUDP packet = PacketUDP.deserialize(api, sharedBuffer);
                        api.getMessageManager().handleReceivedPacket(packet);
                    }
                }
            } catch (Exception e) {
                if (!api.isShuttingDown()) {
                    Printer.printErr("[NetworkManager] Error receiving packet.");
                    e.printStackTrace();
                } else return;
            }
        }
    }

    private void handleGMSPacket(ByteBuffer buffer) throws IOException, ClassNotFoundException {
        GMSReply reply = (GMSReply) PacketTCP.deserialize(buffer);
        if (reply == null)
            return;

        if (reply.getType() != GMSReply.ReplyType.UPDATE)
            api.getGmsManager().handleReceivedPacket(reply);
        else if (reply.getType() == GMSReply.ReplyType.UPDATE)
            api.getMessageManager().handleReceivedPacket(new NormalRequest(reply.getGroupId(), reply));
        else
            Printer.printDebug("[NetworkManager] Received invalid packet from GMS.");
    }

    public void sendNormal(PacketUDP packet, InetSocketAddress destination) throws IOException {
        ByteBuffer buffer = PacketUDP.serialize(packet);

        int bytesSent = normalConnection.getChannel().send(buffer, destination);
        if (bytesSent == 0) {
            Printer.printDebug("[NetworkManager] OS UDP Buffer full! Packet dropped locally.");
        }
    }

    public void sendGms(PacketTCP packet) throws IOException {
        ByteBuffer buffer = PacketTCP.serialize(packet);

        while (buffer.hasRemaining())
            gmsConnection.getChannel().write(buffer);
    }

    public void shutdown() {
        try {
            gmsConnection.close();
            normalConnection.close();
            selector.wakeup();
        } catch (IOException e) {
            Printer.printErr("[NetworkManager] Could not close sockets!");
        }
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPort() {
        return normalConnection.getPort();
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
}
