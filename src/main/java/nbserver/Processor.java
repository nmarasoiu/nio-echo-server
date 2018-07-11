package nbserver;

import java.io.IOException;
import java.nio.channels.*;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

import static java.nio.channels.SelectionKey.OP_READ;
import static nbserver.Config.selectTimeout;
import static nbserver.Util.*;

public final class Processor implements RunnableWithException {
    private final BlockingQueue<SelectableChannel> acceptorQueue;
    private final Pump pump;
    private Selector readSelector;

    Processor(Pump pump, BlockingQueue<SelectableChannel> acceptorQueue) {
        this.pump = pump;
        this.acceptorQueue = acceptorQueue;
    }

    @Override
    public void run() throws IOException, InterruptedException {
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

    private void processConnectionsWithNewData() throws ClosedByInterruptException, InterruptedException {
        if (readSelector.isOpen()) {
            registerConnections();
            select();
            if (!isInterrupted() && readSelector.isOpen()) {
                pump.readAndWrite(readSelector.selectedKeys());
            }
        }
    }

    private void select() throws InterruptedException {
        try {
            readSelector.select(selectTimeout);
        } catch (IOException e) {
            log("IOException in select, will close the selector", e);
            for (SelectionKey key : readSelector.keys()) {
                acceptorQueue.put(key.channel());
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
        if(readSelector.isOpen()) {
            for (SelectionKey key : readSelector.keys()) {
                SelectableChannel channel = key.channel();
                close(channel);
            }
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

    private void closeSelector() {
        try {
            readSelector.close();
        } catch (IOException ignore) {
        }
    }
}
