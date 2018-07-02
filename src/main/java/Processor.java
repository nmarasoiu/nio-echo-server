import sun.net.ConnectionResetException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.FINER;

public final class Processor implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(Processor.class.getName());
    private static final Level PUMP_LEVEL = FINER;
    private static final int BUFFER_SIZE = 4 * 1024 * 1024;
    private final Selector readSelector = Selector.open();
    //    private final Selector connectionEventsSelector = Selector.open();
    private final Queue<SocketChannel> newConnectionsRegisterQueue = new ArrayBlockingQueue<>(1024);
    private final Set<SelectionKey> pendingWrites = new HashSet<>();

    Processor() throws IOException {
    }

    void include(SocketChannel channel) {
        newConnectionsRegisterQueue.add(channel);
    }

    @Override
    public void run() {
        try {
            while (!readSelector.isOpen()) ;
            while (true) {
                registerNewConnections();
//                removeClosedConnectionsFromPending();
                Set<SelectionKey> newPendingKeys = processConnectionsWithNewData();
                processPendingWrites();
                pendingWrites.addAll(newPendingKeys);
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void registerNewConnections() throws ClosedChannelException {
        while (!newConnectionsRegisterQueue.isEmpty()) {
            SocketChannel channel = newConnectionsRegisterQueue.poll();
            SelectionKey key = channel.register(readSelector, SelectionKey.OP_READ);
            key.attach(ByteBuffer.allocateDirect(BUFFER_SIZE));
//                channel.register(connectionEventsSelector, SelectionKey.OP_CONNECT);
        }
    }

    private Set<SelectionKey> processConnectionsWithNewData() {
        try {
            readSelector.select(1);
        } catch (ConnectionResetException e) {
        } catch (IOException e) {
            System.out.println("During readSelect " + e.getMessage());
        }
        return readAndWrite(readSelector.selectedKeys(), true);
    }

    private void processPendingWrites() {
        readAndWrite(new HashSet<>(pendingWrites), false);
    }

    private Set<SelectionKey> readAndWrite(Collection<SelectionKey> keys, boolean normalPass) {
        Set<SelectionKey> newPendingWrites = new HashSet<>();
        for (SelectionKey key : keys) {
            SocketChannel channel = (SocketChannel) key.channel();
            ByteBuffer buffer = bufferOf(key);
            try {
                pump(key, channel, buffer, normalPass);
                if (normalPass && buffer.position() > 0) {
                    newPendingWrites.add(key);
                }
            } catch (IOException e) {
                System.out.println("During readAndWrite: " + e.getMessage() + " cancelling key");
                close(key);
            }
        }
        return newPendingWrites;
    }

    private void close(SelectionKey key) {
        key.cancel();
        pendingWrites.remove(key);
    }

    private ByteBuffer bufferOf(SelectionKey key) {
        return (ByteBuffer) key.attachment();
    }

    private void pump(SelectionKey key, SocketChannel channel, ByteBuffer buffer, boolean normalPass) throws IOException {
        int writeCount, readCount;
        boolean pendingPass = !normalPass;
        while ((readCount = channel.read(buffer)) > 0 || (pendingPass && buffer.position() > 0)) {
            log("Read {0}, buf={1}", readCount, buffer);
            buffer.flip();
            log("Flipped, buf={0}", buffer);
            writeCount = channel.write(buffer);
            log("Written {0}, buf={1}, hasRemaining={2}", writeCount, buffer, buffer.hasRemaining());
            if (buffer.hasRemaining()) {
                buffer.compact();
                log("Compacted, buf={0}", buffer);
            } else {
                buffer.clear();
                log("Cleaned buffer");
            }
        }
        if (readCount == -1) close(key);
    }

    private void log(String msg) {
//        LOGGER.log(PUMP_LEVEL, msg);
    }

    private void log(String msg, Object param) {
//        LOGGER.log(PUMP_LEVEL, msg, param);
    }

    private void log(String msg, Object... params) {
//        if (LOGGER.isLoggable(PUMP_LEVEL)) {
//            LOGGER.log(PUMP_LEVEL, msg, params);
//        }
    }

/*
    private void removeClosedConnectionsFromPending() {
        try {
            connectionEventsSelector.select(1);
        } catch (IOException e) {
            System.out.println("During connectionEventsSelect " + e.getMessage());
        }
        for (SelectionKey key : connectionEventsSelector.selectedKeys()) {
            pendingWrites.remove(key);
            key.cancel();
        }
    }*/
}
