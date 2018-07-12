package nbserver;

import java.net.InetSocketAddress;

import static java.lang.Runtime.getRuntime;

public class Config {
    static final InetSocketAddress BIND_ADDRESS = new InetSocketAddress("localhost", 8081);
    static final int BUFFER_SIZE = 2 * 1024 * 1024;

    static final int ACCEPTOR_QUEUE_CAPACITY = 1200;
    static final long selectTimeout = 1;

    public static final int PROCESSORS = getRuntime().availableProcessors();
}
