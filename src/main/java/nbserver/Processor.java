package nbserver;

import java.io.IOException;
import java.nio.channels.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;
import static java.util.stream.Collectors.toSet;
import static nbserver.Config.SELECT_TIMEOUT;
import static nbserver.Util.*;

public final class Processor implements RunnableWithException {
    private final BlockingQueue<SelectableChannel> acceptorQueue;
    private final Pump pump;
    private Selector readSelector, writeSelector;

    Processor(Pump pump, BlockingQueue<SelectableChannel> acceptorQueue) {
        this.pump = pump;
        this.acceptorQueue = acceptorQueue;
    }

    @Override
    public void run() throws IOException, InterruptedException {
        readSelector = Selector.open();
        writeSelector = Selector.open();
        try {
            while (!isInterrupted() && (readSelector.isOpen() || (writeSelector.isOpen() && pump.hasPendingWrites()))) {
                processChannelsWithNewData();
                processPendingWrites();
            }
        } finally {
            closeChannels();

        }
    }

    private void processPendingWrites() throws InterruptedException {
        if (writeSelector.isOpen()) {
            select(writeSelector);
            Set<ByteChannel> channelsWithRecentWrites = writeSelector.selectedKeys().stream().map(key -> (ByteChannel) key.channel()).collect(toSet());
            pump.readAndWrite(intersection(channelsWithRecentWrites, pump.pendingWrites.keySet()));
        }
    }

    private void processChannelsWithNewData() throws InterruptedException {
        if (readSelector.isOpen()) {
            registerChannels();
            select(readSelector);
            if (!isInterrupted() && readSelector.isOpen()) {
                List<ByteChannel> channels = readSelector.selectedKeys().stream()
                        .map(key -> (ByteChannel) key.channel())
                        .collect(Collectors.toList());
                pump.readAndWrite(channels);
            }
        }
    }

    private void select(Selector selector) throws InterruptedException {
        try {
            selector.select(SELECT_TIMEOUT);
        } catch (IOException e) {
            //todo to treat exceptions locally, or let them bubble up after cleanup/move?
            log("IOException in select, will close the selector and move channels back in the accepted queue", e);
            for (SelectionKey key : readSelector.keys()) {
                acceptorQueue.put(key.channel());
            }
            closeSelectors();
        }
    }

    private void registerChannels() {
        SelectableChannel channel = acceptorQueue.poll();
        if (channel != null) {
            try {
                channel.register(writeSelector, OP_WRITE);
                channel.register(readSelector, OP_READ);
            } catch (ClosedChannelException e) {
                log("Channel is closed when registering, ignoring", e);
            }
        }
    }

    private void closeChannels() {
        try {
            closeRegisteredChannels();
            closeAcceptedButNotRegisteredChannels();
            closeSelectors();
        } catch (Exception e) {
            log("Error, exception thrown in closeChannel on finally: ", e);
        }
    }

    private void closeRegisteredChannels() {
        if (readSelector.isOpen()) {
            for (SelectionKey key : readSelector.keys()) {
                close(key.channel());
            }
        }
    }

    private void closeAcceptedButNotRegisteredChannels() {
        consumeQueue(acceptorQueue, channel -> close(channel));
    }


    private void closeSelectors() {
        close(readSelector);
        close(writeSelector);
    }
}
