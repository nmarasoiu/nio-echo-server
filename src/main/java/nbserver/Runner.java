package nbserver;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
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
        Acceptor acceptor = new Acceptor(BIND_ADDRESS);
        taskFutures = Stream.generate(() -> new Processor(acceptor))
                .limit(PROCESSORS)
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
