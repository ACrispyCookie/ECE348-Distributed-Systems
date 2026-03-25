package dev.ioannikas.group.gms.serializable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class ConnectionTCP {

    private final SocketChannel channel;
    private final InetSocketAddress address;

    public ConnectionTCP(Selector selector, String ipAddress, int port) throws IOException {
        address = new InetSocketAddress(ipAddress, port);
        channel = SocketChannel.open();
        channel.connect(address);
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);
    }

    public void close() throws IOException {
        channel.close();
    }

    public SocketChannel getChannel() {
        return channel;
    }

    public InetSocketAddress getAddress() {
        return address;
    }
}
