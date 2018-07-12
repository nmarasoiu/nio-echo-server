package nbserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.BlockingQueue;

import static nbserver.Util.isInterrupted;

public final class Acceptor implements RunnableWithException {
    private final InetSocketAddress address;
    private final BlockingQueue<SelectableChannel> acceptorQueue;

    Acceptor(InetSocketAddress bindAddress, BlockingQueue<SelectableChannel> queue) {
        address = bindAddress;
        this.acceptorQueue = queue;
    }

    @Override
    public void run() throws IOException, InterruptedException {
        ServerSocketChannel serverSocket = ServerSocketChannel.open().bind(address);
        while (serverSocket.isOpen() && !isInterrupted()) {
            acceptorQueue.put(serverSocket.accept().configureBlocking(false));
        }
    }
}