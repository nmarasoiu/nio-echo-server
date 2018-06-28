import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

public class Processor implements Runnable {
    private final Selector readSelector = Selector.open();
    private final Selector connectionEventsSelector = Selector.open();
    private final Queue<SocketChannel> newConnectionsRegisterQueue = new ArrayBlockingQueue<>(1024);
    private final Set<PendingWrite> pendingWrites = new HashSet<>();

    Processor() throws IOException {
    }

    void include(SocketChannel channel) {
        newConnectionsRegisterQueue.add(channel);
    }

    class PendingWrite {
        final SelectionKey selectionKey;
        final ByteBuffer buffer;
        final UUID uuid;

        PendingWrite(SelectionKey selectionKey, ByteBuffer buffer) {
            this.selectionKey = selectionKey;
            this.buffer = buffer;
            this.uuid = UUID.randomUUID();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PendingWrite that = (PendingWrite) o;
            return Objects.equals(uuid, that.uuid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uuid);
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
                channel.register(readSelector, SelectionKey.OP_READ);
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
            PendingWrite pendingWrite = (PendingWrite) key.attachment();
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
            Object attachment = key.attachment();
            if (attachment != null) {
                pendingWrites.remove(attachment);
                key.cancel();
            }
        }
    }

    private void processPendingWrites() {
        for (PendingWrite pendingWrite : new HashSet<>(pendingWrites)) {
            readAndWrite(pendingWrite.selectionKey, pendingWrite);
        }
    }

    private void readAndWrite(SelectionKey key, PendingWrite pendingWrite) {
        try {
            SocketChannel channel = (SocketChannel) key.channel();
            ByteBuffer buffer = pendingWrite != null ? pendingWrite.buffer : ByteBuffer.allocateDirect(1024);
            pump(channel, buffer);
            setOrRemovePending(key, pendingWrite, buffer);
        } catch (IOException e) {
            System.out.println("During readAndWrite: " + e.getMessage());
            key.cancel();
            if (pendingWrite != null) {
                pendingWrites.remove(pendingWrite);
            }
        }
    }

    private void pump(SocketChannel channel, ByteBuffer buffer) throws IOException {
        boolean canWrite = true;
        while (canWrite && (channel.read(buffer) > 0 || buffer.position() > 0)) {
            buffer.flip();
            int remaining = buffer.remaining();
            if (channel.write(buffer) < remaining) {
                canWrite = false;
            }
            if (buffer.hasRemaining()) {
                buffer.compact();
            } else {
                buffer.clear();
            }
        }
    }

    private void setOrRemovePending(SelectionKey key, PendingWrite pendingWrite, ByteBuffer buffer) {
        if (pendingWrite != null) {
            pendingWrites.remove(pendingWrite);
        }
        if (buffer.position() > 0) {
            if (pendingWrite == null) {
                pendingWrite = new PendingWrite(key, buffer);
            }
            key.attach(pendingWrite);
            pendingWrites.add(pendingWrite);
        } else {
            key.attach(null);
        }
    }
}
