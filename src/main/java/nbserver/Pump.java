package nbserver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.nio.ByteBuffer.allocateDirect;
import static java.nio.channels.SelectionKey.OP_WRITE;
import static java.util.stream.Collectors.toList;
import static nbserver.Config.BUFFER_SIZE;
import static nbserver.Util.isInterrupted;
import static nbserver.Util.log;

final class Pump {
    private final ByteBuffer buffer = allocateDirect(BUFFER_SIZE);

    private final Map<SocketChannel, ByteBuffer> pendingWrites = new LinkedHashMap<>();
    private final Selector writeSelector;

    Pump(Selector writeSelector) {
        this.writeSelector = writeSelector;
    }

    void readAndWritePendingWritesFromChannels(Collection<SocketChannel> channelsWithRecentWrites) throws InterruptedException {
        List<SocketChannel> channels = channelsWithRecentWrites.stream()
                .filter(SocketChannel -> {
                    ByteBuffer buffer = pendingWrites.get(SocketChannel);
                    return buffer != null && isWriting(buffer);
                }).collect(toList());
        if (!channels.isEmpty()) {
            log("channels with recent writes: " + channels.size());
        }
        readAndWrite(channels);
    }

    void readAndWrite(Collection<SocketChannel> channels) throws InterruptedException {
        for (SocketChannel channel : channels) {
            try {
                movePendingBufferIfAnyToMainBuffer(channel);
                boolean writing = isWriting(buffer);
                if (!writing) {
                    int oldPosition = Math.max(buffer.position() - 2, 0);
                    int readCount = channel.read(buffer);
                    if (readCount == -1) {
                        close(channel);
                        return;
                    }
                    writing = scanForDoubleEnter(oldPosition);
                    if (writing) {
                        buffer.flip();
                        Util.writeHeader(buffer.limit(), channel);
                    }
                }
                if (writing && notConsumed()) {
                    int writeCount = channel.write(buffer);
                    if (writeCount == 0) {
                        channel.register(writeSelector, OP_WRITE);
                    }
                }
                if (notConsumed()) {
                    if (!writing) {
                        buffer.flip();
                    }
                    moveToDedicatedBuffer(channel);
                }
            } catch (ClosedChannelException ignore) {
            } catch (IOException e) {
                close(channel);
                log("readAndWrite " + e.getMessage() + ", closing the channel, dropping remaining writes if any");
            } finally {
                buffer.clear();
            }
            if (isInterrupted()) {
                throw new InterruptedException();
            }
        }

    }

    private boolean notConsumed() {
        return buffer.position() < buffer.limit();
    }

    private void moveToDedicatedBuffer(SocketChannel chan) {
        ByteBuffer pendingBuffer = allocateDirect(buffer.limit());
        pendingBuffer.put(buffer);
        pendingBuffer.flip();
        pendingWrites.put(chan, pendingBuffer);
    }

    private boolean isWriting(ByteBuffer buffer) {
        return scanForDoubleEnter(Math.max(0, buffer.limit() - 3));
    }


    private boolean scanForDoubleEnter(int oldPosition) {
        for (int i = oldPosition; i < buffer.position() - 1; i++) {
            if (buffer.get(i) == '\n') {
                byte nextChar = buffer.get(i + 1);
                if (nextChar == '\n' || (nextChar == '\r' && (i + 2 < buffer.limit() && buffer.get(i + 2) == '\n'))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void movePendingBufferIfAnyToMainBuffer(SocketChannel key) {
        ByteBuffer pendingBuffer = pendingWrites.remove(key);
        if (pendingBuffer != null) {
            this.buffer.put(pendingBuffer);
        }
    }

    private void close(SocketChannel key) {
        pendingWrites.remove(key);
        Util.close(key);
    }

    boolean hasPendingWrites() {
        return !pendingWrites.isEmpty();
    }
}
