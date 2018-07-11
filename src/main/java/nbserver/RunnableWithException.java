package nbserver;

import java.io.IOException;

public interface RunnableWithException {
    void run() throws IOException, InterruptedException;
}
