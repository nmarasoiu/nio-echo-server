package nbserver;

import java.io.IOException;
import java.nio.channels.SelectableChannel;

import static java.lang.Thread.currentThread;

final class Util {
    private Util() {
    }

    static void close(SelectableChannel channel) {
        try {
            log("Closing channel "+channel);
            channel.close();
        } catch (IOException ignore) {
        }
    }

    static boolean isInterrupted() {
        return currentThread().isInterrupted();
    }

    static void log(String message, IOException e) {
        log(message);
        e.printStackTrace();
    }

    static void log(String message) {
        System.err.println(message);
    }

}
