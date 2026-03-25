package dev.ioannikas.group.library.serializable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

public class ConnectionUDP {

    private final DatagramChannel channel;
    private final InetSocketAddress address;

    public ConnectionUDP(Selector selector, int port) throws IOException {
        address = new InetSocketAddress(port);
        channel = DatagramChannel.open();
        channel.bind(address);
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);
    }

    public void close() throws IOException {
        channel.close();
    }

    public DatagramChannel getChannel() {
        return channel;
    }

    public InetSocketAddress getAddress() {
        return address;
    }
}
