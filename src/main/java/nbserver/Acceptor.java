package nbserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.ServerSocketChannel;

import static nbserver.Util.isInterrupted;

public class Acceptor implements RunnableWithException {
    private final InetSocketAddress address;
    private final ConsumableBlockingQueue<SelectableChannel> acceptorQueue;

    Acceptor(InetSocketAddress bindAddress, ConsumableBlockingQueue<SelectableChannel> acceptorQueue) {
        address = bindAddress;
        this.acceptorQueue = acceptorQueue;
    }

    @Override
    public void run() throws IOException, InterruptedException {
        ServerSocketChannel serverSocket = ServerSocketChannel.open().bind(address);
        while (serverSocket.isOpen() && !isInterrupted()) {
            acceptorQueue.put(serverSocket.accept().configureBlocking(false));
        }
    }
}