package nbserver;

import java.util.concurrent.Callable;

import static nbserver.Util.log;

public class ExitReporter implements Callable<Void> {
    private final RunnableWithException runnable;

    ExitReporter(RunnableWithException runnable) {
        this.runnable = runnable;
    }

    @Override
    public Void call() throws Exception {
        log("Running " + runnable);
        try {
            runnable.run();
            return null;
        } finally {
            log("Exit " + runnable);
        }

    }
}
