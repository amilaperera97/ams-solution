package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.application.port.*;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.enums.ScanState;
import uk.co.ams.certplatform.domain.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class DefaultScanExecutor implements ScanExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultScanExecutor.class);

    private final ScanRepositoryPort scanRepositoryPort;
    private final AccountRepositoryPort accountRepositoryPort;
    private final CertificateDiscoveryStrategyRegistry strategyRegistry;
    private final CertificateIdentityResolver identityResolver;
    private final CertificateRepositoryPort certificateRepositoryPort;
    
    private final ExecutorService executorService;

    @Value("${certificate-discovery.parallelism.max-regions:5}")
    private int maxParallelRegions;

    @Value("${certificate-discovery.parallelism.max-services-per-region:10}")
    private int maxParallelServices;

    public DefaultScanExecutor(ScanRepositoryPort scanRepositoryPort,
                               AccountRepositoryPort accountRepositoryPort,
                               CertificateDiscoveryStrategyRegistry strategyRegistry,
                               CertificateIdentityResolver identityResolver,
                               CertificateRepositoryPort certificateRepositoryPort) {
        this.scanRepositoryPort = scanRepositoryPort;
        this.accountRepositoryPort = accountRepositoryPort;
        this.strategyRegistry = strategyRegistry;
        this.identityResolver = identityResolver;
        this.certificateRepositoryPort = certificateRepositoryPort;
        // Shared thread pool for all scan executions to avoid creating threads per scan
        this.executorService = Executors.newFixedThreadPool(20); 
    }

    @Override
    public void executeScan(String scanId) {
        Scan scan = scanRepositoryPort.findById(scanId).orElse(null);
        if (scan == null) return;

        try {
            scan.transitionTo(ScanState.RUNNING);
            scanRepositoryPort.save(scan);

            List<Account> targetAccounts = resolveAccounts(scan);
            scan.setAccountsTotal(targetAccounts.size());
            scanRepositoryPort.save(scan);

            List<String> regions = scan.getRegions();
            if (regions == null || regions.isEmpty()) {
                regions = Collections.singletonList("default");
            }
            List<String> services = scan.getServices();

            List<CompletableFuture<DiscoveryResult>> allTasks = new ArrayList<>();

            for (Account account : targetAccounts) {
                CloudProviderType providerType = account.getAuthType() != null ? CloudProviderType.AWS : CloudProviderType.AWS; // simplified
                for (String region : regions) {
                    for (String service : services) {
                        List<CertificateDiscoveryStrategy> strategies = strategyRegistry.findStrategies(providerType, service, null);
                        for (CertificateDiscoveryStrategy strategy : strategies) {
                            ScanContext context = new ScanContext(scanId, account, region, service);
                            
                            CompletableFuture<DiscoveryResult> future = CompletableFuture.supplyAsync(() -> {
                                try {
                                    return strategy.discover(context);
                                } catch (Exception e) {
                                    log.error("Strategy execution failed for {} in region {}", service, region, e);
                                    DiscoveryResult failResult = new DiscoveryResult(providerType.name(), account.getId(), region, service);
                                    failResult.setStatus("FAILED");
                                    failResult.addError(e.getMessage());
                                    return failResult;
                                }
                            }, executorService);
                            allTasks.add(future);
                        }
                    }
                }
            }

            // Wait for all to complete
            CompletableFuture.allOf(allTasks.toArray(new CompletableFuture[0])).join();

            // Aggregate results
            List<DiscoveryResult> results = allTasks.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            List<Certificate> allDiscoveredCertificates = new ArrayList<>();
            boolean hasFailures = false;
            boolean hasSuccesses = false;

            for (DiscoveryResult res : results) {
                if ("FAILED".equals(res.getStatus())) {
                    hasFailures = true;
                } else {
                    hasSuccesses = true;
                    allDiscoveredCertificates.addAll(res.getCertificates());
                }
            }

            // Deduplicate
            List<Certificate> canonicalCertificates = identityResolver.resolve(allDiscoveredCertificates);

            // Persist
            for (Certificate cert : canonicalCertificates) {
                cert.setScanId(scanId);
                certificateRepositoryPort.save(cert);
            }

            scan.setCertificatesDiscovered(canonicalCertificates.size());
            scan.setAccountsCompleted(targetAccounts.size());
            scan.setProgressPercent(100);

            if (hasFailures && hasSuccesses) {
                scan.transitionTo(ScanState.PARTIAL_SUCCESS);
            } else if (hasFailures) {
                scan.transitionTo(ScanState.FAILED);
            } else {
                scan.transitionTo(ScanState.COMPLETED);
            }
            
            scanRepositoryPort.save(scan);

        } catch (Exception e) {
            log.error("Scan orchestration failed", e);
            scan.transitionTo(ScanState.FAILED);
            scanRepositoryPort.save(scan);
        }
    }

    private List<Account> resolveAccounts(Scan scan) {
        List<Account> accounts = new ArrayList<>();
        if (scan.getScopeType() == uk.co.ams.certplatform.domain.enums.ScanScopeType.ACCOUNT) {
            if (scan.getAccountIds() != null) {
                for (String accountId : scan.getAccountIds()) {
                    accountRepositoryPort.findById(accountId).ifPresent(accounts::add);
                }
            }
        } else {
            // Stub for brevity
            Account dummy = new Account();
            dummy.setId("dummy-account");
            dummy.setAuthType(uk.co.ams.certplatform.domain.enums.AccountAuthType.TOKEN);
            accounts.add(dummy);
        }
        return accounts;
    }
}
