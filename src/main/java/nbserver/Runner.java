package nbserver;

import java.nio.channels.SelectableChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static nbserver.Config.ACCEPTOR_QUEUE_CAPACITY;
import static nbserver.Config.BIND_ADDRESS;

public class Runner {
    private final ExecutorService executorService = newCachedThreadPool();
    private final List<Future> taskFutures = new ArrayList<>();

    public static void main(String[] args) {
        new Runner().run();
    }

    private void run() {
        ConsumableBlockingQueue<SelectableChannel> acceptorQueue =
                new ConsumableBlockingQueue<>(new ArrayBlockingQueue<>(ACCEPTOR_QUEUE_CAPACITY));
        Acceptor acceptor = new Acceptor(BIND_ADDRESS, acceptorQueue);
        Processor processor = new Processor(new Pump(), acceptorQueue);
        taskFutures.add(executorService.submit(new ExitReporter(acceptor)));
        taskFutures.add(executorService.submit(new ExitReporter(processor)));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> stop()));
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
