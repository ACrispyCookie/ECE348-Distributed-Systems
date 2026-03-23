package dev.ioannikas.group.gms.singlethread;

import dev.ioannikas.group.gms.GroupManager;
import dev.ioannikas.group.gms.utility.Printer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

@Deprecated
public class SingleThreadGMS {

    private static final int DEFAULT_PORT = 5000;
    private static final Map<SocketAddress, Client> clients = new HashMap<>();
    private static boolean shuttingDown = false;
    private static GroupManager groupManager;
    private static Selector selector;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                Printer.printErr("Invalid port: " + args[0] + ", using default " + DEFAULT_PORT);
            }
        }

        Printer.printInfo("GMS starting...");

        groupManager = new GroupManager();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Printer.printInfo("GMS shutting down...");
            try {
                selector.close();
            } catch (IOException e) {
                Printer.printErr("Error closing server socket: " + e.getMessage());
            }
            onShutdown();
        }));

        try {
            startServer(port);
        } catch (IOException | ClassNotFoundException e) {
            Printer.printErr("Error while running the server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static Client getReceiver(SocketAddress address) {
        return clients.get(address);
    }

    private static void startServer(int port) throws IOException, ClassNotFoundException {
        selector = Selector.open();
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        Printer.printInfo("Waiting for connections on port " + port + "...");

        while (!shuttingDown) {
            selector.select(Client.getTIMEOUT() * 1000L);

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = selectedKeys.iterator();

            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                iterator.remove();

                if (!key.isValid()) {
                    continue;
                }

                if (key.isAcceptable()) {
                    handleAccept(serverChannel, selector);
                } else if (key.isReadable()) {
                    handleRead(key);
                }
            }

            List<SocketAddress> remove = new ArrayList<>();
            for (Map.Entry<SocketAddress, Client> r : clients.entrySet()) {
                if(r.getValue().checkFailure())
                    remove.add(r.getKey());
            }
            remove.forEach(clients::remove);
        }
    }

    private static void handleAccept(ServerSocketChannel serverChannel, Selector selector) throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
        clients.put(clientChannel.getRemoteAddress(), new Client(clientChannel, groupManager));
        Printer.printDebug("Client " + clientChannel.getRemoteAddress() + " connected!");
    }

    private static void handleRead(SelectionKey key) throws IOException, ClassNotFoundException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(4096);


        int bytesRead;
        try {
            bytesRead = clientChannel.read(buffer);
        } catch (Exception e) {
            key.cancel();
            clientChannel.close();
            return;
        }

        if (bytesRead == -1) {
            key.cancel();
            clientChannel.close();
            clients.remove(clientChannel.getRemoteAddress());
            return;
        }
        buffer.flip();

        Client client = getReceiver(clientChannel.getRemoteAddress());
        client.handleRequest(buffer.array(), bytesRead);
    }

    private static void onShutdown() {
        shuttingDown = true;
    }
}
