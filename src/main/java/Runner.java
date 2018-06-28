import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Runner {
    public static void main(String[] args) throws IOException {
        Processor processor1 = new Processor();
        Processor processor2 = new Processor();
        Acceptor acceptor = new Acceptor(8081, processor1, processor2);
        ExecutorService executorService = Executors.newCachedThreadPool();
        executorService.submit(processor1);
        executorService.submit(processor2);
        executorService.submit(acceptor);
    }
}
