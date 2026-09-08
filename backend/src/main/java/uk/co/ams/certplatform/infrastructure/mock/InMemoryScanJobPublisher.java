package com.example.certplatform.infrastructure.mock;

import com.example.certplatform.application.port.ScanJobPublisher;
import com.example.certplatform.application.port.ScanExecutor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class InMemoryScanJobPublisher implements ScanJobPublisher {

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final ScanExecutor scanExecutor;

    public InMemoryScanJobPublisher(@Lazy ScanExecutor scanExecutor) {
        this.scanExecutor = scanExecutor;
    }

    @Override
    public void publish(String scanId) {
        executorService.submit(() -> {
            System.out.println("Processing scan job: " + scanId);
            scanExecutor.executeScan(scanId);
        });
    }
}
