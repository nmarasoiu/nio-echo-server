package nbserver;

import java.nio.channels.ClosedByInterruptException;

import static nbserver.Util.log;

public final class ExitReporter implements Runnable {
    private final RunnableWithException runnable;

    ExitReporter(RunnableWithException task) {
        this.runnable = task;
    }

    @Override
    public void run() {
        log("Running " + runnable);
        try {
            runnable.run();
        } catch (ClosedByInterruptException ignore) {
        } catch (Exception e) {
            log("Caught exception in task " + this, e);
        } finally {
            log("Exit " + runnable);
        }
    }
}
