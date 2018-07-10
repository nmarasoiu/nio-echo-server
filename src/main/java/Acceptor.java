import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class Acceptor implements Runnable {

    private final int port;
    private final Processor processor;

    Acceptor(int port, Processor processor) {
        this.port = port;
        this.processor = processor;
    }

    public void run() {
        try {
            ServerSocketChannel serverSocket = ServerSocketChannel.open();
            serverSocket.bind(new InetSocketAddress("localhost", port));
            while (true) {
                SocketChannel connection = serverSocket.accept();
                connection.configureBlocking(false);
                processor.register(connection);
            }
        } catch (ClosedByInterruptException e) {
        } catch
                (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}