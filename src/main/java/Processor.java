import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

public final class Processor implements Runnable {
    private static final int BUFFER_SIZE = 2 * 1024 * 1024;
    private final Queue<SocketChannel> newConnectionsRegisterQueue = new ArrayBlockingQueue<>(1024);
    private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    private Map<SelectionKey, ByteBuffer> pendingWrites = new LinkedHashMap<>();
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
                Map<SelectionKey, ByteBuffer> selectedKeysWithPendingWrites =
                        readAndWrite(readSelector.selectedKeys());
                Map<SelectionKey, ByteBuffer> pendingKeysStillPending =
                        readAndWrite(new HashSet<>(pendingWrites.keySet()));
                pendingWrites = new LinkedHashMap<>(selectedKeysWithPendingWrites);
                pendingWrites.putAll(pendingKeysStillPending);
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

    private Map<SelectionKey, ByteBuffer> readAndWrite(Set<SelectionKey> keys) {
        Map<SelectionKey, ByteBuffer> selectedKeysWithPendingWrites = new HashMap<>();
        for (SelectionKey key : keys) {
            try {
                //copy any pending writes into the buffer
                if (pendingWrites.containsKey(key)) {
                    buffer.put(pendingWrites.remove(key));
                }
                //pump loop
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
                    ByteBuffer pendingBuffer = ByteBuffer.allocateDirect(buffer.limit());
                    pendingBuffer.put(buffer);
                    selectedKeysWithPendingWrites.put(key, pendingBuffer);
                }
                buffer.clear();
            } catch (IOException e) {
                close(key);//we go on with next key, do not exit the loop on exception
                log("readAndWrite" + e.getMessage());
            }
        }
        return selectedKeysWithPendingWrites;
    }

    private void close(SelectionKey key) {
        key.cancel();
        pendingWrites.remove(key);
    }

    private void log(String s) {
        System.err.println(s);
    }
}
