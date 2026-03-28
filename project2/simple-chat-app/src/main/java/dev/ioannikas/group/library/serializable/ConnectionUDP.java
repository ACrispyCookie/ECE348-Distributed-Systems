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
        channel = DatagramChannel.open();
        channel.bind(new InetSocketAddress(port));
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);
        address = (InetSocketAddress) channel.getLocalAddress();
    }

    public void close() throws IOException {
        channel.socket().close();
        channel.close();
    }

    public DatagramChannel getChannel() {
        return channel;
    }

    public int getPort() {
        return address.getPort();
    }
}
