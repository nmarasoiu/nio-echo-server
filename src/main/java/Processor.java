import sun.net.ConnectionResetException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

public class Processor implements Runnable {
    private static final int BUFFER_SIZE = 1024;
    private final Selector readSelector = Selector.open();
    private final Selector connectionEventsSelector = Selector.open();
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
                processConnectionsWithNewData();
                removeClosedConnectionsFromPending();
                while (!pendingWrites.isEmpty()) {
                    processPendingWrites();
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void registerNewConnections() {
        for (SocketChannel channel : newConnectionsRegisterQueue) {
            try {
                try {
                    SelectionKey key = channel.register(readSelector, SelectionKey.OP_READ);
                    key.attach(ByteBuffer.allocateDirect(BUFFER_SIZE));
                    channel.register(connectionEventsSelector, SelectionKey.OP_CONNECT);
                } catch (CancelledKeyException e) {
                    //todo why some channels are already registered with a cancelled key, this is just for newly accepted connections..
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void processConnectionsWithNewData() {
        try {
            readSelector.select(1);
        } catch (ConnectionResetException e) {
        } catch (IOException e) {
            System.out.println("During readSelect " + e.getMessage());
        }
        for (SelectionKey key : readSelector.selectedKeys()) {
            readAndWrite(key);
        }
    }

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
    }

    private void processPendingWrites() {
        for (SelectionKey key : new HashSet<>(pendingWrites)) {
            readAndWrite(key);
        }
    }

    private void readAndWrite(SelectionKey key) {
        ByteBuffer buffer = bufferOf(key);
        try {
            SocketChannel channel = (SocketChannel) key.channel();
            pump(channel, buffer);
            setOrRemovePending(key, buffer);
        } catch (IOException e) {
            System.out.println("During readAndWrite: " + e.getMessage());
            key.cancel();
            pendingWrites.remove(key);
        }
    }

    private ByteBuffer bufferOf(SelectionKey key) {
        return (ByteBuffer) key.attachment();
    }

    // returns true if the channel was drained, false is more is available in buffer or channel but it cannot write anymore (buffer full)
    private void pump(SocketChannel channel, ByteBuffer buffer) throws IOException {
        boolean canWrite = true;
        while (canWrite && (channel.read(buffer) > 0 || buffer.position() > 0)) {
            buffer.flip();
            canWrite = channel.write(buffer) > 0;
            if (buffer.hasRemaining()) {
                buffer.compact();
            } else {
                buffer.clear();
            }
        }
    }

    private void setOrRemovePending(SelectionKey key, ByteBuffer buffer) {
        pendingWrites.remove(key);
        if (buffer.position() > 0) {
            pendingWrites.add(key);
        }
    }
}
