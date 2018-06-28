import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class Acceptor implements Runnable {

    private final int port;
    private final Processor processor1;
    private final Processor processor2;

    public Acceptor(int port, Processor processor1, Processor processor2) {
        this.port = port;
        this.processor1 = processor1;
        this.processor2 = processor2;
    }

    public void run() {
        try {
            int i = 0;
            ServerSocketChannel serverSocket = ServerSocketChannel.open();
            serverSocket.bind(new InetSocketAddress("localhost", port));
            while (!serverSocket.isOpen()) ;
            while (true) {
                SocketChannel connection = serverSocket.accept();
                connection.configureBlocking(false);
                Processor processor = (i++) % 2 == 0 ? processor1 : processor2;
                processor.include(connection);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}