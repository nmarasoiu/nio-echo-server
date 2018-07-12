package nbserver;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

import static java.lang.Thread.currentThread;

final class Util {
    private Util() {
    }

    static <T> void consumeQueue(BlockingQueue<T> blockingQueue, Consumer<T> activity) {
        for (T elem = blockingQueue.poll(); elem != null; elem = blockingQueue.poll()) {
            activity.accept(elem);
        }
    }

    static void close(Closeable closeable) {
        try {
            log("Closing " + closeable);
            closeable.close();
        } catch (IOException e) {
            log("While closing: ", e);
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
