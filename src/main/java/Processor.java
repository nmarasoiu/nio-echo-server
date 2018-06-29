import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

public class Processor implements Runnable {
    private final Selector readSelector = Selector.open();
    private final Selector connectionEventsSelector = Selector.open();
    private final Queue<SocketChannel> newConnectionsRegisterQueue = new ArrayBlockingQueue<>(1024);
    private final Set<BufferAndCtx> pendingWrites = new HashSet<>();

    Processor() throws IOException {
    }

    void include(SocketChannel channel) {
        newConnectionsRegisterQueue.add(channel);
    }

    final class BufferAndCtx {
        final SelectionKey selectionKey;
        final ByteBuffer buffer;

        BufferAndCtx(SelectionKey selectionKey, ByteBuffer buffer) {
            this.selectionKey = selectionKey;
            this.buffer = buffer;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BufferAndCtx that = (BufferAndCtx) o;
            return Objects.equals(selectionKey, that.selectionKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(selectionKey);
        }
    }

    @Override
    public void run() {
        try {
            while (!readSelector.isOpen()) ;
            while (true) {
                registerNewConnections();
                processConnectionsWithNewData();
                removeClosedConnectionsFromPending();
                processPendingWrites();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void registerNewConnections() {
        for (SocketChannel channel : newConnectionsRegisterQueue) {
            try {
                channel.register(connectionEventsSelector, SelectionKey.OP_CONNECT);
                SelectionKey key = channel.register(readSelector, SelectionKey.OP_READ);
                key.attach(new BufferAndCtx(key, ByteBuffer.allocateDirect(1024*1024)));
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
    }

    private void processConnectionsWithNewData() {
        try {
            readSelector.select(10);
        } catch (IOException e) {
            System.out.println("During readSelect " + e.getMessage());
        }
        for (SelectionKey key : readSelector.selectedKeys()) {
            BufferAndCtx pendingWrite = (BufferAndCtx) key.attachment();
            readAndWrite(key, pendingWrite);
        }
    }

    private void removeClosedConnectionsFromPending() {
        try {
            connectionEventsSelector.select(1);
        } catch (IOException e) {
            System.out.println("During connectionEventsSelect " + e.getMessage());
        }
        for (SelectionKey key : connectionEventsSelector.selectedKeys()) {
            BufferAndCtx bufAndCtx = (BufferAndCtx) key.attachment();
            pendingWrites.remove(bufAndCtx);
            key.cancel();
        }
    }

    private void processPendingWrites() {
        for (BufferAndCtx pendingWrite : new HashSet<>(pendingWrites)) {
            readAndWrite(pendingWrite.selectionKey, pendingWrite);
        }
    }

    private void readAndWrite(SelectionKey key, BufferAndCtx pendingWrite) {
        try {
            SocketChannel channel = (SocketChannel) key.channel();
            ByteBuffer buffer = pendingWrite.buffer;
            pump(channel, buffer);
            setOrRemovePending(pendingWrite, buffer);
        } catch (IOException e) {
            System.out.println("During readAndWrite: " + e.getMessage());
            key.cancel();
            pendingWrites.remove(pendingWrite);
        }
    }

    private void pump(SocketChannel channel, ByteBuffer buffer) throws IOException {
        boolean canWrite = true;
        while (canWrite && (channel.read(buffer) > 0 || buffer.position() > 0)) {
            buffer.flip();
            int remaining = buffer.remaining();
            if (channel.write(buffer) < remaining) {
//                canWrite = false;
            }
            if (buffer.hasRemaining()) {
                buffer.compact();
            } else {
                buffer.clear();
            }
        }
    }

    private void setOrRemovePending(BufferAndCtx pendingWrite, ByteBuffer buffer) {
        pendingWrites.remove(pendingWrite);
        if (buffer.position() > 0) {
            pendingWrites.add(pendingWrite);
        }
    }
}
