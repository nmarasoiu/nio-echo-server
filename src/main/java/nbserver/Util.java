package nbserver;

import java.io.Closeable;
import java.io.IOException;

import static java.lang.Thread.currentThread;

final class Util {
    private Util() {
    }

    static void close(Closeable closeable) {
        try {
            log("Closing " + closeable);
            closeable.close();
        } catch (IOException e) {
            log("While closing: ",e);
        }
    }

    static boolean isInterrupted() {
        return currentThread().isInterrupted();
    }

    static void log(String message, Throwable e) {
        log(message);
        e.printStackTrace();
    }

    static void log(String message) {
        System.err.println(message);
    }

}
