package nbserver;

import java.net.InetSocketAddress;

import static java.lang.Runtime.getRuntime;

final class Config {
    static final InetSocketAddress BIND_ADDRESS = new InetSocketAddress("localhost", 8081);
    static final int BUFFER_SIZE = 2 * 1024 * 1024;
    static final int PROCESSORS = 2 * getRuntime().availableProcessors();

    private Config() {
    }
}
