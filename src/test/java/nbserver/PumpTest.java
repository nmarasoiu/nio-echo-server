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
import java.nio.channels.ByteChannel;

import static java.util.Collections.singletonList;
import static org.mockito.Mockito.*;

@RunWith(BlockJUnit4ClassRunner.class)
public class PumpTest {

    @Mock
    private ByteChannel channel;

    @Before
    public void setup(){
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void readAndWrite() throws IOException, InterruptedException {
        when(channel.read(any())).thenAnswer((Answer) invocation -> {
            ByteBuffer buf = getBuf(invocation);
            buf.put((byte)' ');
            buf.put((byte) '\n');
            buf.put((byte) '\n');
            return 3;
        }).thenReturn(-1);
        when(channel.write(any())).thenAnswer(invocation -> {
            getBuf(invocation).position(3);
            return 3;
        });
        new Pump().readAndWrite(singletonList(channel));
        verify(channel).read(any());
        verify(channel).write(any());
    }

    private ByteBuffer getBuf(InvocationOnMock invocation) {
        return (ByteBuffer) invocation.getArguments()[0];
    }
}