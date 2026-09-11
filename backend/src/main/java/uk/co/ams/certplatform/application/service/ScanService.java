package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.application.port.ScanJobPublisher;
import uk.co.ams.certplatform.application.port.ScanRepositoryPort;
import uk.co.ams.certplatform.domain.enums.ScanScopeType;
import uk.co.ams.certplatform.domain.enums.ScanState;
import uk.co.ams.certplatform.domain.model.Scan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ScanService {

    private final ScanRepositoryPort scanRepositoryPort;
    private final ScanJobPublisher scanJobPublisher;

    public ScanService(ScanRepositoryPort scanRepositoryPort, ScanJobPublisher scanJobPublisher) {
        this.scanRepositoryPort = scanRepositoryPort;
        this.scanJobPublisher = scanJobPublisher;
    }

    @Transactional
    public Scan createScan(String name, ScanScopeType scopeType, List<String> providerIds, List<String> environmentIds, List<String> accountIds, List<String> regions, List<String> services) {
        // Validate scope
        if (scopeType == ScanScopeType.ACCOUNT && (accountIds == null || accountIds.isEmpty())) {
            throw new IllegalArgumentException("Account IDs are required for ACCOUNT scope");
        }

        Instant now = Instant.now();
        Scan scan = Scan.builder()
                .id("scan-" + UUID.randomUUID())
                .name(name)
                .scopeType(scopeType)
                .providerIds(providerIds)
                .environmentIds(environmentIds)
                .accountIds(accountIds)
                .regions(regions)
                .services(services)
                .status(ScanState.REQUESTED)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Scan savedScan = scanRepositoryPort.save(scan);

        // Transition to QUEUED
        savedScan = scanRepositoryPort.save(savedScan.transitionTo(ScanState.QUEUED));

        // Queue the job
        scanJobPublisher.publish(savedScan.id());

        return savedScan;
    }

    public Optional<Scan> getScan(String id) {
        return scanRepositoryPort.findById(id);
    }

    public List<Scan> getAllScans() {
        return scanRepositoryPort.findAll();
    }

    @Transactional
    public Scan cancelScan(String id) {
        Scan scan = scanRepositoryPort.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scan not found: " + id));
        return scanRepositoryPort.save(scan.transitionTo(ScanState.CANCEL_REQUESTED));
    }

    @Transactional
    public Scan retryScan(String id) {
        Scan scan = scanRepositoryPort.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scan not found: " + id));
        
        if (scan.status() != ScanState.FAILED && scan.status() != ScanState.PARTIAL_SUCCESS) {
            throw new IllegalStateException("Can only retry FAILED or PARTIAL_SUCCESS scans.");
        }

        // A terminal scan cannot transition, so re-queueing deliberately resets the state.
        Scan requeued = scanRepositoryPort.save(scan.withStatus(ScanState.QUEUED));
        scanJobPublisher.publish(requeued.id());
        return requeued;
    }

    @Transactional
    public Scan runAgain(String id) {
        Scan existingScan = scanRepositoryPort.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scan not found: " + id));
        
        return createScan(
                existingScan.name() + " (Run Again)",
                existingScan.scopeType(),
                existingScan.providerIds(),
                existingScan.environmentIds(),
                existingScan.accountIds(),
                existingScan.regions(),
                existingScan.services()
        );
    }
}
