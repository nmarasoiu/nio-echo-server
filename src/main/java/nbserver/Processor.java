package nbserver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.BlockingQueue;

import static java.lang.Thread.currentThread;
import static java.nio.channels.SelectionKey.OP_READ;
import static nbserver.Config.selectTimeout;
import static nbserver.Util.isInterrupted;
import static nbserver.Util.log;

public final class Processor implements Runnable {
    private final BlockingQueue<SelectableChannel> acceptorQueue;
    private final Pump pump;
    private Selector readSelector;

    Processor(Pump pump, BlockingQueue<SelectableChannel> acceptorQueue) {
        this.pump = pump;
        this.acceptorQueue = acceptorQueue;
    }

    @Override
    public void run() {
        openSelector();
        while (!isInterrupted() && (readSelector.isOpen() || pump.hasPendingWrites())) {
            processConnectionsWithNewData();
            pump.readAndWritePendingWrites();
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

    private void openSelector() {
        try {
            readSelector = Selector.open();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
            try {
                readSelector.close();
            } catch (IOException e1) {
                //ignore
            }
        }
    }

    private void registerConnections() {
        while (!acceptorQueue.isEmpty()) {
            SelectableChannel channel = acceptorQueue.remove();
            try {
                channel.register(readSelector, OP_READ);
            } catch (ClosedChannelException e) {
                log("Channel is closed when registering, ignoring", e);
            }
        }
    }
}
