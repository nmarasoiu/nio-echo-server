package nbserver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static java.nio.ByteBuffer.allocateDirect;
import static nbserver.Config.BUFFER_SIZE;
import static nbserver.Pump.StreamState.EOF;
import static nbserver.Pump.StreamState.OPEN;
import static nbserver.Util.isInterrupted;
import static nbserver.Util.log;

final class Pump {
    enum StreamState {EOF, OPEN;}

    private final ByteBuffer buffer = allocateDirect(BUFFER_SIZE);
    private final Map<SelectionKey, ByteBuffer> pendingWrites = new LinkedHashMap<>();

    void readAndWritePendingWrites() throws InterruptedException {
        readAndWrite(new HashSet<>(pendingWrites.keySet()));
    }

    void readAndWrite(Set<SelectionKey> keys) throws InterruptedException {
        for (SelectionKey key : keys) {
            try {
                movePendingBufferIfAnyToMainBuffer(key);
                StreamState streamState = copyUntilReadOrWriteBlocks((SocketChannel) key.channel());
                if (streamState == EOF) {
                    close(key);
                    log("Pump: EOF on " + key.channel());
                } else if (unwrittenBytes()) {
                    moveToDedicatedBuffer(key);
                }
            } catch (ClosedByInterruptException e) {
                log("Pump: interrupted");
                //interrupt status already set
            } catch (IOException e) {
                close(key);
                log("readAndWrite " + e.getMessage() + ", closing the key, dropping remaining writes if any");
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

    private void moveToDedicatedBuffer(SelectionKey key) {
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
        boolean metDoubleEnter = false;
        for (int i = 0; i < buffer.position() - 1; i++) {
            if (buffer.get(i) == '\n') {
                byte nextChar = buffer.get(i + 1);
                if (nextChar == '\n' || (nextChar == '\r' && buffer.get(i + 2) == '\n')) {
                    metDoubleEnter = true;
                    break;
                }
            }
        }
        return metDoubleEnter;
    }

    private boolean writeBufferToSocket(WritableByteChannel channel) throws IOException {
        buffer.flip();
        boolean canWrite = channel.write(buffer) > 0;
        buffer.compact();
        return canWrite;
    }

    private void movePendingBufferIfAnyToMainBuffer(SelectionKey key) {
        ByteBuffer pendingBuffer = pendingWrites.remove(key);
        if (pendingBuffer != null) buffer.put(pendingBuffer);
    }

    private void close(SelectionKey key) {
        pendingWrites.remove(key);
        key.cancel();
        Util.close(key.channel());
    }

    boolean hasPendingWrites() {
        return !pendingWrites.isEmpty();
    }

}
