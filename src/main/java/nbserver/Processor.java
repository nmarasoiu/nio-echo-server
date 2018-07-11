package nbserver;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

import static java.nio.channels.SelectionKey.OP_READ;
import static nbserver.Config.selectTimeout;
import static nbserver.Util.*;

public final class Processor implements RunnableWithException {
    private final ConsumableBlockingQueue<SelectableChannel> acceptorQueue;
    private final Pump pump;
    private Selector readSelector;

    Processor(Pump pump, ConsumableBlockingQueue<SelectableChannel> acceptorQueue) {
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
            try {
                closeConnections();
            } catch (Throwable t) {
                log("Error, exception thrown in closeConnection on finally: ", t);
            }
        }
    }

    private void processConnectionsWithNewData() throws InterruptedException {
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
        acceptorQueue.consumeQueue(channel -> {
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
        close(readSelector);
    }

    private void closeRegisteredConnections() {
        if (readSelector.isOpen()) {
            for (SelectionKey key : readSelector.keys()) {
                SelectableChannel channel = key.channel();
                close(channel);
            }
        }
    }

    private void closeAcceptedButNotRegisteredConnections() {
        acceptorQueue.consumeQueue(channel -> close(channel));
    }

}
