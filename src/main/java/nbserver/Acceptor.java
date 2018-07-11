package nbserver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.BlockingQueue;

import static java.lang.Thread.currentThread;
import static nbserver.Util.isInterrupted;

public class Acceptor implements Runnable {
    private final InetSocketAddress address;
    private final BlockingQueue<SelectableChannel> acceptorQueue;

    Acceptor(InetSocketAddress bindAddress, BlockingQueue<SelectableChannel> acceptorQueue) {
        address = bindAddress;
        this.acceptorQueue = acceptorQueue;
    }

    public void run() {
        try {
            ServerSocketChannel serverSocket = ServerSocketChannel.open();
            serverSocket.bind(address);
            while (serverSocket.isOpen() && !isInterrupted()) {
                SocketChannel connection = serverSocket.accept();
                connection.configureBlocking(false);
                acceptorQueue.put(connection);
            }
        } catch (ClosedByInterruptException | InterruptedException e) {
            currentThread().interrupt();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}