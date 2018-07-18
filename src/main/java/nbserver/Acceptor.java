package nbserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import static nbserver.Util.log;

final class Acceptor {
    private final ServerSocketChannel serverSocket;

    Acceptor(InetSocketAddress bindAddress) throws IOException {
        serverSocket = (ServerSocketChannel) ServerSocketChannel.open().bind(bindAddress).configureBlocking(false);
    }

    SelectableChannel accept() {
        try {
            SocketChannel channel = serverSocket.accept();
            return channel != null ? channel.configureBlocking(false) : null;
        } catch (IOException e) {
            log("Exception in acccept", e);
            return null;
        }
    }
}