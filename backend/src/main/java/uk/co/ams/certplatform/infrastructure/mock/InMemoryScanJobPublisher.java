package uk.co.ams.certplatform.infrastructure.mock;

import uk.co.ams.certplatform.application.port.ScanExecutor;
import uk.co.ams.certplatform.application.port.ScanJobPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs scans in-process. Stands in for a real queue until one is needed; a SQS
 * or Rabbit publisher would slot in behind the same port.
 *
 * <p>Publishing waits for the enclosing transaction to commit. {@code ScanService}
 * creates the scan and publishes it inside one transaction, so handing the id to
 * another thread immediately is a race: the worker looks the scan up, does not
 * find it because the insert has not committed, and silently does nothing. Any
 * queue-backed publisher would hit exactly the same race, which is why the fix
 * belongs here rather than in the service.
 */
@Component
public class InMemoryScanJobPublisher implements ScanJobPublisher {

    private static final Logger log = LoggerFactory.getLogger(InMemoryScanJobPublisher.class);

    private final ExecutorService executorService =
            Executors.newSingleThreadExecutor(namedDaemonThreads("scan-job"));
    private final ScanExecutor scanExecutor;

    public InMemoryScanJobPublisher(@Lazy ScanExecutor scanExecutor) {
        this.scanExecutor = scanExecutor;
    }

    @Override
    public void publish(String scanId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(scanId);
                }
            });
            return;
        }
        submit(scanId);
    }

    private void submit(String scanId) {
        executorService.submit(() -> {
            log.info("Processing scan job {}", scanId);
            try {
                scanExecutor.executeScan(scanId);
            } catch (RuntimeException e) {
                // The worker thread is the last line of defence; a thrown exception
                // here would kill the task silently and leave the scan stuck RUNNING.
                log.error("Scan job {} failed", scanId, e);
            }
        });
    }

    private static java.util.concurrent.ThreadFactory namedDaemonThreads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
