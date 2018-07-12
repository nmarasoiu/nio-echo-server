package nbserver;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
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

    static void writeHeader(int length, WritableByteChannel channel) throws IOException {
        channel.write(
                Charset.forName("UTF-8").encode("HTTP/1.1 200 OK\n" +
//                                "Date: Mon, 27 Jul 2009 12:28:53 GMT\n" +
//                                "Server: Apache/2.2.14 (Win32)\n" +
//                                "Last-Modified: Wed, 22 Jul 2009 19:15:56 GMT\n" +
                        "Content-Length: " + length + "\n" +
//                                "Content-Type: text/html\n" +
//                                "Connection: Closed\n" +
                        "\n"));
    }
}
