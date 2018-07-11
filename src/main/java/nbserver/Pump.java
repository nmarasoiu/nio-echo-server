package nbserver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static java.nio.ByteBuffer.allocateDirect;
import static nbserver.Config.BUFFER_SIZE;
import static nbserver.Pump.StreamState.EOF;
import static nbserver.Pump.StreamState.OPEN;
import static nbserver.Util.isInterrupted;

final class Pump {
    enum StreamState {EOF, OPEN}

    private final ByteBuffer buffer = allocateDirect(BUFFER_SIZE);
    private final Map<SelectionKey, ByteBuffer> pendingWrites = new LinkedHashMap<>();

    void readAndWritePendingWrites() {
        readAndWrite(new HashSet<>(pendingWrites.keySet()));
    }

    void readAndWrite(Set<SelectionKey> keys) {
        for (SelectionKey key : keys) {
            try {
                movePendingBufferIfAnyToMainBuffer(key);
                StreamState streamState = copyUntilReadOrWriteBlocks(key);
                if (streamState == EOF) {
                    close(key);
                } else if (unwrittenBytes()) {
                    moveToDedicatedBuffer(key);
                }
            } catch (ClosedByInterruptException ignore) {
            } catch (IOException e) {
                close(key);
                Util.log("readAndWrite " + e.getMessage() + ", closing the key, dropping remaining writes if any", e);
            } finally {
                buffer.clear();
            }
            if (isInterrupted()) break;
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

    private StreamState copyUntilReadOrWriteBlocks(SelectionKey key) throws IOException {
        int readCount;
        boolean canWrite = true;
        SocketChannel channel = (SocketChannel) key.channel();
        while ((readCount = channel.read(buffer)) > 0 || (canWrite && unwrittenBytes())) {
            buffer.flip();
            canWrite = channel.write(buffer) > 0;
            buffer.compact();
        }
        return readCount == -1 ? EOF : OPEN;
    }

    private void movePendingBufferIfAnyToMainBuffer(SelectionKey key) {
        ByteBuffer pendingBuffer = pendingWrites.remove(key);
        if (pendingBuffer != null) buffer.put(pendingBuffer);
    }

    private void close(SelectionKey key) {
        pendingWrites.remove(key);
        key.cancel();
    }

    boolean hasPendingWrites() {
        return !pendingWrites.isEmpty();
    }
}
