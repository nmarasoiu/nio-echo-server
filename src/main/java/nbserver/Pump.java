package nbserver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
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
    private static final Charset charset = Charset.forName("UTF-8");
    private static final String newlines = "\n\n\n\n";
    private static final ByteBuffer headerBuf = charset.encode("HTTP/1.1 200 OK\n" +
            "Content-Length: " + newlines);
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
                    final int oldPosition;
                    if (buffer.position() == 0) {
                        headerBuf.rewind();
                        buffer.put(headerBuf);
                        oldPosition = buffer.position();
                    } else {
                        oldPosition = Math.max(buffer.position() - 2, 0);
                    }
                    int readCount = channel.read(buffer);
//                    log("readCount=" + readCount);
                    if (receivedEof(channel, readCount)) return;
                    writing = scanForDoubleEnter(oldPosition);
                    if (writing) {
                        writeLengthHeader();
                        buffer.flip();
                    } else {
                        log("dblEnter not found after a read pass");
                    }
                }
                if (writing && notConsumed()) {
                    int writeCount = channel.write(buffer);
//                    log("writeCount=" + writeCount);
                    if (writeCount == 0) {
                        log("Registering to writeSel, count was 0");
                        channel.register(writeSelector, OP_WRITE);
                    }
                }
                if (notConsumed()) {
                    log("moving to dedicated");
                    if (!writing) {
                        buffer.flip();
                    }
                    moveToDedicatedBuffer(channel);
                }
            } catch (ClosedChannelException ignore) {
            } catch (IOException e) {
                receivedEof(channel);
                if (!e.getMessage().contains("Connection reset by peer")) {
                    if (!e.getMessage().contains("Broken pipe")) {
                        log("readAndWrite " + e.getMessage() + ", closing the channel, dropping remaining writes if any");
                    }
                }
            } finally {
                buffer.clear();
            }
            if (isInterrupted()) {
                throw new InterruptedException();
            }
        }
    }

    private boolean receivedEof(SocketChannel channel, int readCount) {
        if (readCount == -1) {
            receivedEof(channel);
            return true;
        }
        return false;
    }

    private void writeLengthHeader() {
        int length = buffer.position() - headerBuf.limit();
        int position = buffer.position();
        buffer.position(headerBuf.limit() - newlines.length());
        buffer.put(charset.encode(String.valueOf(length)));
        buffer.position(position);
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

    private void receivedEof(SocketChannel key) {
        pendingWrites.remove(key);
        Util.close(key);
    }

    boolean hasPendingWrites() {
        return !pendingWrites.isEmpty();
    }
}
