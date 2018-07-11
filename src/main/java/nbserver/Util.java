package nbserver;

import java.io.IOException;

import static java.lang.Thread.currentThread;

final class Util {
    private Util() {
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
