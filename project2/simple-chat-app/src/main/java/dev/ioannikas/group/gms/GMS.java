package dev.ioannikas.group.gms;

import dev.ioannikas.group.gms.utility.GroupManager;
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

public class GMS {

    private static final int DEFAULT_PORT = 10100;
    private static final Map<SocketAddress, ClientHandler> clients = new HashMap<>();
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
            onShutdown();
            try {
                selector.close();
            } catch (IOException e) {
                Printer.printErr("Error closing server socket: " + e.getMessage());
            }
        }));

        startServer(port);
    }

    public static ClientHandler getReceiver(SocketAddress address) {
        return clients.get(address);
    }

    private static void startServer(int port) {
        ServerSocketChannel serverChannel;
        try {
            selector = Selector.open();
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.bind(new InetSocketAddress(port));
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            Printer.printInfo("Waiting for connections on port " + port + "...");
        } catch (IOException e) {
            Printer.printErr("Error while starting the server: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        while (!shuttingDown) {
            try {
                selector.select(ClientHandler.getTIMEOUT() * 1000L);

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
                for (Map.Entry<SocketAddress, ClientHandler> r : clients.entrySet()) {
                    if(r.getValue().checkFailure())
                        remove.add(r.getKey());
                }
                remove.forEach(clients::remove);
            }  catch (IOException | ClassNotFoundException e) {
                Printer.printErr("Error while running the server: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void handleAccept(ServerSocketChannel serverChannel, Selector selector) throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ);
        clients.put(clientChannel.getRemoteAddress(), new ClientHandler(clientChannel, groupManager));
    }

    private static void handleRead(SelectionKey key) throws IOException, ClassNotFoundException {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ClientHandler clientHandler = null;

        try {
            clientHandler = getReceiver(clientChannel.getRemoteAddress());
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            int bytesRead = clientChannel.read(buffer);

            if (bytesRead == -1) {
                clients.remove(clientChannel.getRemoteAddress());
                clientHandler.handleFailure();
                key.cancel();
                clientChannel.close();
                return;
            }

            buffer.flip();
            clientHandler.handleRequest(buffer);

        } catch (IOException | ClassNotFoundException e) {
            Printer.printInfo("Client forcefully disconnected.");

            try {
                if (clientHandler != null) {
                    clients.remove(clientChannel.getRemoteAddress());
                    clientHandler.handleFailure();
                }
                key.cancel();
                clientChannel.close();
            } catch (IOException cleanupException) {
                Printer.printErr("Failed to clean up dead socket.");
            }
        }
    }

    public static int connectedClients() {
        return groupManager.getClientCount();
    }

    private static void onShutdown() {
        shuttingDown = true;
    }
}
