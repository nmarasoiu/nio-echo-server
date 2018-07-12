package nbserver;

import java.io.IOException;
import java.nio.channels.*;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

import static java.nio.channels.SelectionKey.OP_READ;
import static nbserver.Config.SELECT_TIMEOUT;
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
            try {
                closeConnections();
            } catch (Exception e) {
                log("Error, exception thrown in closeConnection on finally: ", e);
            }
        }
    }

    private void processConnectionsWithNewData() throws InterruptedException {
        if (readSelector.isOpen()) {
            registerConnections();
            select();
            if (!isInterrupted() && readSelector.isOpen()) {
                pump.readAndWrite(readSelector.selectedKeys().stream()
                        .map(key -> (ByteChannel) key.channel()).collect(Collectors.toList()));
            }
        }
    }

    private void select() throws InterruptedException {
        try {
            readSelector.select(SELECT_TIMEOUT);
        } catch (IOException e) {
            log("IOException in select, will close the selector", e);
            for (SelectionKey key : readSelector.keys()) {
                acceptorQueue.put(key.channel());
            }
        }
    }

    private void registerConnections() {
        SelectableChannel channel = acceptorQueue.poll();
        if (channel != null) {
            try {
                channel.register(readSelector, OP_READ);
            } catch (ClosedChannelException e) {
                log("Channel is closed when registering, ignoring", e);
            }
        }
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
        consumeQueue(acceptorQueue, channel -> close(channel));
    }

}
