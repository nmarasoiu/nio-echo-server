import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

import static java.nio.ByteBuffer.allocateDirect;
import static java.util.Optional.ofNullable;

public final class Processor implements Runnable {
    private static final int BUFFER_SIZE = 2 * 1024 * 1024;
    private final Queue<SocketChannel> newConnectionsRegisterQueue = new ArrayBlockingQueue<>(1024);
    private final ByteBuffer buffer = allocateDirect(BUFFER_SIZE);
    private final Map<SelectionKey, ByteBuffer> pendingWrites = new LinkedHashMap<>();
    private Selector readSelector;

    void register(SocketChannel channel) {
        newConnectionsRegisterQueue.add(channel);
    }

    @Override
    public void run() {
        try {
            readSelector = Selector.open();
            while (!readSelector.isOpen()) ;
            while (true) {
                registerNewConnections();
                readSelector.select(1);
                readAndWrite(readSelector.selectedKeys());
                readAndWrite(new HashSet<>(pendingWrites.keySet()));
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void registerNewConnections() throws ClosedChannelException {
        while (!newConnectionsRegisterQueue.isEmpty()) {
            SocketChannel channel = newConnectionsRegisterQueue.remove();
            channel.register(readSelector, SelectionKey.OP_READ);
        }
    }

    private void readAndWrite(Set<SelectionKey> keys) {
        for (SelectionKey key : keys) {
            try {
                ofNullable(pendingWrites.remove(key))
                        .ifPresent(pendingBuffer -> buffer.put(pendingBuffer));
                int readCount;
                boolean canWrite = true;
                SocketChannel channel = (SocketChannel) key.channel();
                while ((readCount = channel.read(buffer)) > 0
                        || (canWrite && buffer.position() > 0)) {
                    buffer.flip();
                    canWrite = channel.write(buffer) > 0;
                    buffer.compact();
                }
                if (readCount == -1) {
                    close(key);
                } else if (buffer.position() > 0) {
                    buffer.flip();
                    ByteBuffer pendingBuffer = allocateDirect(buffer.limit());
                    pendingBuffer.put(buffer);
                    pendingBuffer.flip();
                    pendingWrites.put(key, pendingBuffer);
                }
            } catch (IOException e) {
                close(key);//we go on with next key
                log("readAndWrite " + e.getMessage());
            } finally {
                buffer.clear();
            }
        }
    }

    private void close(SelectionKey key) {
        pendingWrites.remove(key);
        key.cancel();
    }

    private void log(String s) {
        System.err.println(s);
    }
}
