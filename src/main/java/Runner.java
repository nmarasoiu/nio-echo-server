import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Runner {
    public static void main(String[] args) throws IOException {
        Processor processor = new Processor();
        Acceptor acceptor = new Acceptor(8081, processor);
        ExecutorService executorService = Executors.newCachedThreadPool();
        executorService.submit(processor);
        executorService.submit(acceptor);
    }
}
