package nbserver;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import static java.lang.Thread.currentThread;
import static java.nio.channels.SelectionKey.OP_READ;
import static nbserver.Config.selectTimeout;
import static nbserver.Util.isInterrupted;
import static nbserver.Util.log;

public final class Processor implements RunnableWithException {
    private final BlockingQueue<SelectableChannel> acceptorQueue;
    private final Pump pump;
    private Selector readSelector;

    Processor(Pump pump, BlockingQueue<SelectableChannel> acceptorQueue) {
        this.pump = pump;
        this.acceptorQueue = acceptorQueue;
    }

    @Override
    public void run() throws IOException {
        readSelector = Selector.open();
        try {
            while (!isInterrupted() && (readSelector.isOpen() || pump.hasPendingWrites())) {
                processConnectionsWithNewData();
                pump.readAndWritePendingWrites();
            }
        } finally {
            closeConnections();
        }
    }

    private void processConnectionsWithNewData() {
        if (readSelector.isOpen()) {
            registerConnections();
            select();
            if (!isInterrupted() && readSelector.isOpen()) {
                pump.readAndWrite(readSelector.selectedKeys());
            }
        }
    }

    private void select() {
        try {
            readSelector.select(selectTimeout);
        } catch (IOException e) {
            log("IOException in select, will close the selector", e);
            for (SelectionKey key : readSelector.keys()) {
                try {
                    acceptorQueue.put(key.channel());
                } catch (InterruptedException e1) {
                    currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void registerConnections() {
        consumeQueue(channel -> {
            try {
                channel.register(readSelector, OP_READ);
            } catch (ClosedChannelException e) {
                log("Channel is closed when registering, ignoring", e);
            }
        });
    }

    private void closeConnections() {
        closeRegisteredConnections();
        closeAcceptedButNotRegisteredConnections();
        closeSelector();
    }

    private void closeRegisteredConnections() {
        for (SelectionKey key : readSelector.keys()) {
            SelectableChannel channel = key.channel();
            close(channel);
        }
    }

    private void closeAcceptedButNotRegisteredConnections() {
        consumeQueue(channel -> close(channel));
    }

    private void consumeQueue(Consumer<SelectableChannel> activity) {
        SelectableChannel pendingConnection = acceptorQueue.poll();
        while (pendingConnection != null) {
            activity.accept(pendingConnection);
            pendingConnection = acceptorQueue.poll();
        }
    }

    private void close(SelectableChannel channel) {
        try {
            channel.close();
        } catch (IOException ignore) {
        }
    }

    private void closeSelector() {
        try {
            readSelector.close();
        } catch (IOException ignore) {
        }
    }
}
