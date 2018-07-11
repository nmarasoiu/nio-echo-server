package nbserver;

import static nbserver.Util.log;

public class ExitReporter implements Runnable {
    private final Runnable runnable;

    public ExitReporter(Runnable runnable) {
        this.runnable = runnable;
    }

    @Override
    public void run() {
        log("Running "+runnable);
        try {
            runnable.run();
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            log("Exit " + runnable);
        }

    }
}
