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
        Scan scan = new Scan();
        scan.setId("scan-" + UUID.randomUUID().toString());
        scan.setName(name);
        scan.setScopeType(scopeType);
        scan.setProviderIds(providerIds);
        scan.setEnvironmentIds(environmentIds);
        scan.setAccountIds(accountIds);
        scan.setRegions(regions);
        scan.setServices(services);
        scan.setStatus(ScanState.REQUESTED);
        scan.setCreatedAt(Instant.now());
        scan.setUpdatedAt(Instant.now());

        // Validate scope
        if (scopeType == ScanScopeType.ACCOUNT && (accountIds == null || accountIds.isEmpty())) {
            throw new IllegalArgumentException("Account IDs are required for ACCOUNT scope");
        }
        
        Scan savedScan = scanRepositoryPort.save(scan);
        
        // Transition to QUEUED
        savedScan.transitionTo(ScanState.QUEUED);
        savedScan = scanRepositoryPort.save(savedScan);

        // Queue the job
        scanJobPublisher.publish(savedScan.getId());

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
        scan.transitionTo(ScanState.CANCEL_REQUESTED);
        return scanRepositoryPort.save(scan);
    }

    @Transactional
    public Scan retryScan(String id) {
        Scan scan = scanRepositoryPort.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scan not found: " + id));
        
        if (scan.getStatus() != ScanState.FAILED && scan.getStatus() != ScanState.PARTIAL_SUCCESS) {
            throw new IllegalStateException("Can only retry FAILED or PARTIAL_SUCCESS scans.");
        }
        
        scan.setStatus(ScanState.QUEUED);
        scan = scanRepositoryPort.save(scan);
        scanJobPublisher.publish(scan.getId());
        return scan;
    }

    @Transactional
    public Scan runAgain(String id) {
        Scan existingScan = scanRepositoryPort.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scan not found: " + id));
        
        return createScan(
                existingScan.getName() + " (Run Again)",
                existingScan.getScopeType(),
                existingScan.getProviderIds(),
                existingScan.getEnvironmentIds(),
                existingScan.getAccountIds(),
                existingScan.getRegions(),
                existingScan.getServices()
        );
    }
}
