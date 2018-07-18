package nbserver;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import static java.util.Collections.singletonList;
import static org.mockito.Mockito.*;

@RunWith(BlockJUnit4ClassRunner.class)
public class PumpTest {

    @Mock
    private SocketChannel channel;
    @Mock
    private Selector writeselector;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void readAndWrite() throws IOException, InterruptedException {
        when(channel.read(anyBBuf())).thenAnswer((Answer) invocation -> {
            ByteBuffer buf = getBuf(invocation);
            buf.put((byte) ' ');
            buf.put((byte) '\n');
            buf.put((byte) '\n');
            return 3;
        }).thenReturn(-1);
        when(channel.write(anyBBuf())).thenAnswer(invocation -> {
            getBuf(invocation).position(3);
            return 3;
        });
        new Pump(writeselector).readAndWrite(singletonList(channel));
        verify(channel).read(anyBBuf());
        verify(channel).write(anyBBuf());
    }

    private ByteBuffer anyBBuf() {
        return any(ByteBuffer.class);
    }

    private ByteBuffer getBuf(InvocationOnMock invocation) {
        return (ByteBuffer) invocation.getArguments()[0];
    }
}