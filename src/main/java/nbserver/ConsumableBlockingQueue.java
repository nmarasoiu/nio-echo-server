package nbserver;

import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

public class ConsumableBlockingQueue<T> /*implements BlockingQueue<T>*/ {
    private final BlockingQueue<T> blockingQueue;

    public ConsumableBlockingQueue(BlockingQueue<T> blockingQueue) {
        this.blockingQueue = blockingQueue;
    }

    public void put(T elem) throws InterruptedException {
        blockingQueue.put(elem);
    }

    void consumeQueue(Consumer<T> activity) {
        T elem = poll();
        while (elem != null) {
            activity.accept(elem);
            elem = poll();
        }
    }

    private T poll() {
        return blockingQueue.poll();
    }

}
