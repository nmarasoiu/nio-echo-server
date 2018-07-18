package nbserver;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Stream;

import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static nbserver.Config.BIND_ADDRESS;
import static nbserver.Config.PROCESSORS;

public class Runner {
    private final ExecutorService executorService = newCachedThreadPool();
    private volatile Iterable<Future<?>> taskFutures;

    public static void main(String[] args) throws IOException {
        new Runner().run();
    }

    private void run() throws IOException {
        ArrayBlockingQueue<SocketChannel> queue = new ArrayBlockingQueue<>(12000);
        Acceptor acceptor = new Acceptor(BIND_ADDRESS, queue);
        taskFutures = Stream.concat(Stream.of(acceptor), Stream.generate(() -> new Processor(queue)).limit(PROCESSORS))
                .map(task -> executorService.submit(new ExitReporter(task)))
                .collect(toList());
        getRuntime().addShutdownHook(new Thread(() -> stop()));
    }

    private boolean stop() {
        for (Future future : taskFutures) {
            future.cancel(true);
        }
        executorService.shutdown();
        boolean terminatedCleanly = false;
        try {
            terminatedCleanly = executorService.awaitTermination(10, SECONDS);
        } catch (InterruptedException ignored) {
        }
        Util.log("Stop: " +
                (terminatedCleanly ? "terminated cleanly" : "timeout or interrupted while waiting for pool shutdown"));
        return terminatedCleanly;
    }

}
