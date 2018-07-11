package nbserver;

import java.net.InetSocketAddress;

public class Config {
    static final InetSocketAddress BIND_ADDRESS = new InetSocketAddress("localhost", 8081);
    static final int BUFFER_SIZE = 2 * 1024 * 1024;

    static final int ACCEPTOR_QUEUE_CAPACITY = 12;
    static final long selectTimeout = 1;
}