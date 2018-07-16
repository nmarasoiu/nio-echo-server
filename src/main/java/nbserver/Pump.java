package nbserver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ClosedChannelException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static java.nio.ByteBuffer.allocateDirect;
import static java.util.stream.Collectors.toList;
import static nbserver.Config.BUFFER_SIZE;
import static nbserver.Util.isInterrupted;
import static nbserver.Util.log;

final class Pump {
    private final ByteBuffer buffer = allocateDirect(BUFFER_SIZE);

    private final Map<ByteChannel, ByteBuffer> pendingWrites = new LinkedHashMap<>();

    void readAndWritePendingWritesFromChannels(Set<ByteChannel> channelsWithRecentWrites) throws InterruptedException {
        readAndWrite(channelsWithRecentWrites.stream()
                .filter(byteChannel -> pendingWrites.containsKey(byteChannel))
                .filter(byteChannel -> isWriting(pendingWrites.get(byteChannel))).collect(toList()));
    }

    void readAndWrite(Iterable<ByteChannel> channels) throws InterruptedException {
        for (ByteChannel channel : channels) {
            try {
                movePendingBufferIfAnyToMainBuffer(channel);
                boolean writing = isWriting(buffer);
                if (!writing) {
                    int oldPosition = Math.max(buffer.position()-2,0);
                    int readCount = channel.read(buffer);
                    if(readCount==-1){
                        close(channel);
                        return;
                    }
                    writing = scanForDoubleEnter(oldPosition);
                    if (writing) {
                        buffer.flip();
                        Util.writeHeader(buffer.limit(), channel);
                    }
                }
                if (writing) {
                    channel.write(buffer);
                    buffer.compact();
                }
                if (unwrittenBytes()) {
                    if(!writing){
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

    private boolean unwrittenBytes() {
        return buffer.position() > 0;
    }

    private void moveToDedicatedBuffer(ByteChannel key) {
        ByteBuffer pendingBuffer = allocateDirect(buffer.limit());
        pendingBuffer.put(buffer);
        pendingBuffer.flip();
        pendingWrites.put(key, pendingBuffer);
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

    private void movePendingBufferIfAnyToMainBuffer(ByteChannel key) {
        ByteBuffer pendingBuffer = pendingWrites.remove(key);
        if (pendingBuffer != null) {
            this.buffer.put(pendingBuffer);
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
