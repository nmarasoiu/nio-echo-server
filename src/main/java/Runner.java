import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class Runner {
    public static void main(String[] args) {
        Processor processor = new Processor();
        Acceptor acceptor = new Acceptor(8081, processor);
        Executors.newSingleThreadExecutor().submit(acceptor);
        processor.run();
    }
}
