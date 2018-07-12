package nbserver;

import java.nio.channels.SelectableChannel;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static nbserver.Config.BIND_ADDRESS;
import static nbserver.Config.PROCESSORS;

public class Runner {
    private final ExecutorService executorService = newCachedThreadPool();
    private volatile Iterable<Future<Void>> taskFutures;

    public static void main(String[] args) {
                new Runner().run();
    }

    private void run() {
        BlockingQueue<SelectableChannel> acceptorQueue = new SynchronousQueue<>();
        Acceptor acceptor = new Acceptor(BIND_ADDRESS, acceptorQueue);
        taskFutures = Stream.concat(Stream.of(acceptor),
                Stream.generate(() -> new Processor(new Pump(), acceptorQueue)).limit(PROCESSORS))
                .map(task -> executorService.submit(new ExitReporter(task))).collect(toList());
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
