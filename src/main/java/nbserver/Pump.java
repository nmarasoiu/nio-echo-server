package nbserver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.WritableByteChannel;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.ByteBuffer.allocateDirect;
import static nbserver.Config.BUFFER_SIZE;
import static nbserver.Pump.StreamState.EOF;
import static nbserver.Pump.StreamState.OPEN;
import static nbserver.Util.isInterrupted;
import static nbserver.Util.log;

final class Pump {
    enum StreamState {EOF, OPEN;}

    private final ByteBuffer buffer = allocateDirect(BUFFER_SIZE);
    final static Map<ByteChannel, ByteBuffer> pendingWrites = new ConcurrentHashMap<>();//todo writeSelector

    void readAndWrite(Iterable<ByteChannel> channels) throws InterruptedException {
        for (ByteChannel channel : channels) {
            try {
                movePendingBufferIfAnyToMainBuffer(channel);
                StreamState streamState = copyUntilReadOrWriteBlocks(channel);
                if (streamState == EOF) {
                    close(channel);
                    log("Pump: EOF on " + channel);
                } else if (unwrittenBytes()) {
                    moveToDedicatedBuffer(channel);
                }
            } catch (ClosedByInterruptException e) {
                log("Pump: interrupted");
                //interrupt status already set
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

    private boolean unwrittenBytes() {
        return buffer.position() > 0;
    }

    private void moveToDedicatedBuffer(ByteChannel key) {
        buffer.flip();
        ByteBuffer pendingBuffer = allocateDirect(buffer.limit());
        pendingBuffer.put(buffer);
        pendingBuffer.flip();
        pendingWrites.put(key, pendingBuffer);
    }

    private StreamState copyUntilReadOrWriteBlocks(ByteChannel channel) throws IOException {
        int readCount;
        boolean canWrite = true, firstWrite = true, metDoubleEnter = false;
        while ((readCount = channel.read(buffer)) > 0 || (metDoubleEnter && canWrite && unwrittenBytes())) {
            metDoubleEnter = scanForDoubleEnter();
            if (metDoubleEnter) {
                if (firstWrite) {
                    Util.writeHeader(buffer.position(), channel);
                    firstWrite = false;
                }
                canWrite = writeBufferToSocket(channel);
            }
        }
        return readCount == -1 ? EOF : OPEN;
    }

    private boolean scanForDoubleEnter() {
        for (int i = 0; i < buffer.position() - 1; i++) {
            if (buffer.get(i) == '\n') {
                byte nextChar = buffer.get(i + 1);
                if (nextChar == '\n' || (nextChar == '\r' && (i + 2 < buffer.limit() && buffer.get(i + 2) == '\n'))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean writeBufferToSocket(WritableByteChannel channel) throws IOException {
        buffer.flip();
        boolean canWrite = channel.write(buffer) > 0;
        buffer.compact();
        return canWrite;
    }

    private void movePendingBufferIfAnyToMainBuffer(ByteChannel key) {
        ByteBuffer pendingBuffer = pendingWrites.remove(key);
        if (pendingBuffer != null) {
            buffer.put(pendingBuffer);
        }
    }

    private void close(ByteChannel key) {
        pendingWrites.remove(key);
        Util.close(key);
    }

    boolean hasPendingWrites() {
        return !pendingWrites.isEmpty();
    }

}
