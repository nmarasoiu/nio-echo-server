package nbserver;

import java.io.IOException;
import java.nio.channels.*;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.nio.channels.SelectionKey.OP_READ;
import static nbserver.Util.*;

public final class Processor implements RunnableWithException {
    private final Acceptor acceptor;
    private final Pump pump;
    private Selector readSelector, writeSelector;

    Processor(Acceptor acceptor) {
        this.pump = new Pump(writeSelector);
        this.acceptor = acceptor;
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
        if (writeSelector.isOpen() && pump.hasPendingWrites()) {
            List<SocketChannel> selectedChannels = select(writeSelector, false);
            pump.readAndWritePendingWritesFromChannels(selectedChannels);
        }
    }

    private void processChannelsWithNewData() throws InterruptedException {
        if (readSelector.isOpen()) {
            registerChannels();
            List<SocketChannel> channels = select(readSelector, true);
            if (!isInterrupted() && readSelector.isOpen()) {
                pump.readAndWrite(channels);
            }
        }
    }

    private List<SocketChannel> select(Selector selector, boolean read) {
        try {
            selector.selectNow();
            List<SocketChannel> channels = Collections.unmodifiableList(selector.selectedKeys().stream()
                    .map(key -> (SocketChannel) key.channel())
                    .collect(Collectors.toList()));
            if (read) {
                selector.selectedKeys().retainAll(Collections.emptySet());
            } else {
                selector.selectedKeys().forEach(key -> key.cancel());
            }
            return channels;
        } catch (IOException e) {
            log("During select", e);
            return Collections.emptyList();
        }
    }

    private void registerChannels() {
        SelectableChannel channel = acceptor.accept();
        if (channel != null) {
            try {
                channel.register(readSelector, OP_READ);
            } catch (ClosedChannelException e) {
                log("Channel is closed when registering, ignoring", e);
            }
        }
    }

    private void closeChannels() {
        try {
            closeRegisteredChannels();
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

    private void closeSelectors() {
        close(readSelector);
        close(writeSelector);
    }
}
