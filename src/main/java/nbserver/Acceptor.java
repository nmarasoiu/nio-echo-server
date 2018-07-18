package nbserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.BlockingQueue;

import static nbserver.Util.isInterrupted;
import static nbserver.Util.log;

final class Acceptor implements RunnableWithException {
    private final ServerSocketChannel serverSocket;
    private final BlockingQueue<SocketChannel> queue;

    Acceptor(InetSocketAddress bindAddress, BlockingQueue<SocketChannel> queue) throws IOException {
        serverSocket = ServerSocketChannel.open().bind(bindAddress);
        this.queue = queue;
    }

    @Override
    public void run() throws IOException, InterruptedException {
        while (!isInterrupted()) {
            try {
                SocketChannel channel = (SocketChannel) serverSocket.accept().configureBlocking(false);
                queue.put(channel);
            } catch (IOException e) {
                log("Exception in accept", e);
            }
        }
    }
}