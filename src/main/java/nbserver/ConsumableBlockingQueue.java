package nbserver;

import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

class ConsumableBlockingQueue<T> /*implements BlockingQueue<T>*/ {
    private final BlockingQueue<T> blockingQueue;

    ConsumableBlockingQueue(BlockingQueue<T> blockingQueue) {
        this.blockingQueue = blockingQueue;
    }

    void put(T elem) throws InterruptedException {
        blockingQueue.put(elem);
    }

    void consumeQueue(Consumer<T> activity) {
        for (T elem = poll(); elem != null; elem = poll()) {
            activity.accept(elem);
        }
    }

    private T poll() {
        return blockingQueue.poll();
    }

}
