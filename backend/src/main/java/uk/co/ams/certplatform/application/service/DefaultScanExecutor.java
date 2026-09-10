package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.application.port.*;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.enums.DiscoveryStatus;
import uk.co.ams.certplatform.domain.enums.ScanScopeType;
import uk.co.ams.certplatform.domain.enums.ScanState;
import uk.co.ams.certplatform.domain.model.*;
import uk.co.ams.certplatform.infrastructure.ratelimit.AccountRateLimiters;
import uk.co.ams.certplatform.shared.config.DiscoveryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs a scan: plan the work, fan it out, fold the results back together.
 *
 * <p>Knows nothing about any particular cloud or service. It resolves which
 * provider each account belongs to, asks {@link DiscoveryTaskPlanner} for the
 * task list, and dispatches each task to whichever strategy the registry holds
 * for it. Adding a service or a cloud does not touch this class.
 */
@Service
public class DefaultScanExecutor implements ScanExecutor {

    private static final Logger log = LoggerFactory.getLogger(DefaultScanExecutor.class);

    private final ScanRepositoryPort scanRepositoryPort;
    private final AccountRepositoryPort accountRepositoryPort;
    private final ProviderRepositoryPort providerRepositoryPort;
    private final EnvironmentRepositoryPort environmentRepositoryPort;
    private final CertificateDiscoveryStrategyRegistry strategyRegistry;
    private final DiscoveryTaskPlanner planner;
    private final CertificateIdentityResolver identityResolver;
    private final CertificateRepositoryPort certificateRepositoryPort;
    private final AccountRateLimiters rateLimiters;
    private final DiscoveryProperties properties;

    private final ExecutorService executorService;
    private final Semaphore globalConcurrency;

    public DefaultScanExecutor(ScanRepositoryPort scanRepositoryPort,
                               AccountRepositoryPort accountRepositoryPort,
                               ProviderRepositoryPort providerRepositoryPort,
                               EnvironmentRepositoryPort environmentRepositoryPort,
                               CertificateDiscoveryStrategyRegistry strategyRegistry,
                               DiscoveryTaskPlanner planner,
                               CertificateIdentityResolver identityResolver,
                               CertificateRepositoryPort certificateRepositoryPort,
                               AccountRateLimiters rateLimiters,
                               DiscoveryProperties properties) {
        this.scanRepositoryPort = scanRepositoryPort;
        this.accountRepositoryPort = accountRepositoryPort;
        this.providerRepositoryPort = providerRepositoryPort;
        this.environmentRepositoryPort = environmentRepositoryPort;
        this.strategyRegistry = strategyRegistry;
        this.planner = planner;
        this.identityResolver = identityResolver;
        this.certificateRepositoryPort = certificateRepositoryPort;
        this.rateLimiters = rateLimiters;
        this.properties = properties;

        int poolSize = Math.max(1, properties.getParallelism().getMaxConcurrentTasks());
        this.executorService = Executors.newFixedThreadPool(poolSize, namedDaemonThreads("cert-discovery"));
        this.globalConcurrency = new Semaphore(poolSize);
    }

    @Override
    public void executeScan(String scanId) {
        Scan scan = scanRepositoryPort.findById(scanId).orElse(null);
        if (scan == null) {
            log.warn("Scan {} no longer exists; nothing to run", scanId);
            return;
        }

        try {
            scan.transitionTo(ScanState.RUNNING);
            scanRepositoryPort.save(scan);

            List<DiscoveryTaskPlanner.AccountTarget> targets = resolveTargets(scan);
            scan.setAccountsTotal(targets.size());
            scanRepositoryPort.save(scan);

            Map<String, String> environmentByAccount = environmentNamesOf(targets);

            DiscoveryTaskPlanner.Plan plan = planner.plan(targets, scan.getRegions(), scan.getServices());
            log.info("Scan {}: {} account(s) -> {} discovery task(s){}", scanId, targets.size(),
                    plan.tasks().size(),
                    plan.unknownServices().isEmpty() ? "" : "; unknown services " + plan.unknownServices());

            List<DiscoveryResult> results = runTasks(scanId, plan.tasks());

            persistAndSummarise(scan, plan, results, targets.size(), environmentByAccount);

        } catch (Exception e) {
            log.error("Scan {} failed to orchestrate", scanId, e);
            failScan(scan);
        }
    }

    // --- planning inputs -----------------------------------------------------

    /**
     * Expands the scan's scope into concrete accounts, each paired with the cloud
     * it belongs to. The provider comes from the account's provider record, so a
     * mixed-cloud organisation dispatches correctly without any special-casing.
     */
    private List<DiscoveryTaskPlanner.AccountTarget> resolveTargets(Scan scan) {
        Map<String, Account> accountsById = new LinkedHashMap<>();
        for (Account account : resolveAccounts(scan)) {
            if (account.getId() != null) accountsById.putIfAbsent(account.getId(), account);
        }

        Map<String, CloudProviderType> providerCache = new LinkedHashMap<>();
        List<DiscoveryTaskPlanner.AccountTarget> targets = new ArrayList<>();
        for (Account account : accountsById.values()) {
            targets.add(new DiscoveryTaskPlanner.AccountTarget(account, providerTypeOf(account, providerCache)));
        }
        return targets;
    }

    private List<Account> resolveAccounts(Scan scan) {
        ScanScopeType scope = scan.getScopeType();
        List<Account> accounts = new ArrayList<>();

        if (scope == ScanScopeType.ACCOUNT && scan.getAccountIds() != null) {
            for (String accountId : scan.getAccountIds()) {
                accountRepositoryPort.findById(accountId).ifPresent(accounts::add);
            }
            return accounts;
        }

        if (scope == ScanScopeType.ENVIRONMENT && scan.getEnvironmentIds() != null) {
            for (String environmentId : scan.getEnvironmentIds()) {
                accounts.addAll(accountRepositoryPort.findByEnvironmentId(environmentId));
            }
            return accounts;
        }

        if (scope == ScanScopeType.PROVIDER && scan.getProviderIds() != null) {
            for (String providerId : scan.getProviderIds()) {
                for (Environment environment : environmentRepositoryPort.findByProviderId(providerId)) {
                    accounts.addAll(accountRepositoryPort.findByEnvironmentId(environment.getId()));
                }
            }
            return accounts;
        }

        // CUSTOM: union of everything named, at whichever level it was named.
        if (scan.getProviderIds() != null) {
            for (String providerId : scan.getProviderIds()) {
                for (Environment environment : environmentRepositoryPort.findByProviderId(providerId)) {
                    accounts.addAll(accountRepositoryPort.findByEnvironmentId(environment.getId()));
                }
            }
        }
        if (scan.getEnvironmentIds() != null) {
            for (String environmentId : scan.getEnvironmentIds()) {
                accounts.addAll(accountRepositoryPort.findByEnvironmentId(environmentId));
            }
        }
        if (scan.getAccountIds() != null) {
            for (String accountId : scan.getAccountIds()) {
                accountRepositoryPort.findById(accountId).ifPresent(accounts::add);
            }
        }
        return accounts;
    }

    /**
     * Environment name per account, resolved once. Certificates carry it because
     * "which certificate is about to expire in production" is the question the
     * inventory exists to answer - and the column is NOT NULL, so leaving it unset
     * silently loses every certificate in the scan.
     */
    private Map<String, String> environmentNamesOf(List<DiscoveryTaskPlanner.AccountTarget> targets) {
        Map<String, String> byAccountId = new LinkedHashMap<>();
        Map<String, String> byEnvironmentId = new LinkedHashMap<>();
        for (DiscoveryTaskPlanner.AccountTarget target : targets) {
            Account account = target.account();
            String environmentId = account.getEnvironmentId();
            if (environmentId == null) continue;
            String name = byEnvironmentId.computeIfAbsent(environmentId,
                    id -> environmentRepositoryPort.findById(id).map(Environment::getName).orElse(id));
            byAccountId.put(account.getId(), name);
        }
        return byAccountId;
    }

    private CloudProviderType providerTypeOf(Account account, Map<String, CloudProviderType> cache) {
        String providerId = account.getProviderId();
        if (providerId == null) return CloudProviderType.AWS;
        return cache.computeIfAbsent(providerId, id -> providerRepositoryPort.findById(id)
                .map(Provider::getType)
                .orElse(CloudProviderType.AWS));
    }

    // --- fan-out -------------------------------------------------------------

    private List<DiscoveryResult> runTasks(String scanId, List<DiscoveryTask> tasks) {
        DiscoveryOptions options = properties.toDiscoveryOptions();
        // Per-account bound keeps one large account from consuming the whole pool.
        Map<String, Semaphore> perAccount = new LinkedHashMap<>();
        int perAccountLimit = Math.max(1, properties.getParallelism().getMaxConcurrentTasksPerAccount());

        List<CompletableFuture<DiscoveryResult>> futures = new ArrayList<>(tasks.size());
        for (DiscoveryTask task : tasks) {
            Semaphore accountGate = perAccount.computeIfAbsent(
                    String.valueOf(task.account().getId()), id -> new Semaphore(perAccountLimit));
            futures.add(CompletableFuture.supplyAsync(
                    () -> runOne(scanId, task, options, accountGate), executorService));
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(properties.getParallelism().getScanTimeoutMinutes(), TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Scan {} did not finish within the timeout; collecting whatever completed", scanId, e);
        }

        List<DiscoveryResult> results = new ArrayList<>(futures.size());
        for (int i = 0; i < futures.size(); i++) {
            CompletableFuture<DiscoveryResult> future = futures.get(i);
            DiscoveryTask task = tasks.get(i);
            if (future.isDone() && !future.isCompletedExceptionally()) {
                results.add(future.join());
            } else {
                future.cancel(true);
                results.add(DiscoveryResult.forTask(task)
                        .failed("Did not complete within the scan timeout"));
            }
        }
        return results;
    }

    private DiscoveryResult runOne(String scanId, DiscoveryTask task, DiscoveryOptions options, Semaphore accountGate) {
        DiscoveryServiceDescriptor descriptor = task.descriptor();

        if (properties.isDisabled(descriptor.qualifiedKey(), descriptor.key())) {
            return DiscoveryResult.forTask(task).skipped("Disabled by certificate-discovery.disabled-services");
        }

        Optional<CertificateDiscoveryStrategy> strategy = strategyRegistry.strategyFor(descriptor);
        if (strategy.isEmpty()) {
            return DiscoveryResult.forTask(task).failed("No strategy registered for " + descriptor.qualifiedKey());
        }

        boolean globalHeld = false;
        boolean accountHeld = false;
        try {
            globalConcurrency.acquire();
            globalHeld = true;
            accountGate.acquire();
            accountHeld = true;
            rateLimiters.forAccount(task.account().getId()).acquire();

            ScanContext context = ScanContext.forTask(scanId, task, options);
            DiscoveryResult result = strategy.get().discover(context);
            return result != null ? result
                    : DiscoveryResult.forTask(task).failed("Strategy returned no result");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return DiscoveryResult.forTask(task).failed("Interrupted before the service could be read");
        } catch (Exception e) {
            // A strategy is not supposed to throw, but one bad service must not sink the scan.
            log.error("Discovery task {} threw", task, e);
            return DiscoveryResult.forTask(task).failed(rootMessage(e));
        } finally {
            if (accountHeld) accountGate.release();
            if (globalHeld) globalConcurrency.release();
        }
    }

    // --- folding results back ------------------------------------------------

    private void persistAndSummarise(Scan scan, DiscoveryTaskPlanner.Plan plan, List<DiscoveryResult> results,
                                     int accountCount, Map<String, String> environmentByAccount) {
        List<Certificate> discovered = new ArrayList<>();
        Set<String> accountsWithOutcome = new LinkedHashSet<>();
        AtomicInteger failures = new AtomicInteger();
        AtomicInteger successes = new AtomicInteger();

        for (DiscoveryResult result : results) {
            accountsWithOutcome.add(String.valueOf(result.getAccountId()));
            DiscoveryStatus status = result.getStatus();
            if (status.isFailure()) {
                failures.incrementAndGet();
            } else if (status.ran()) {
                successes.incrementAndGet();
            }
            // PARTIAL still carries certificates, so collect from anything that ran.
            if (!status.isFailure()) {
                discovered.addAll(result.getCertificates());
            }
        }

        List<Certificate> canonical = identityResolver.resolve(discovered);
        int persisted = 0;
        for (Certificate certificate : canonical) {
            certificate.setScanId(scan.getId());
            if (certificate.getEnvironment() == null) {
                certificate.setEnvironment(environmentByAccount.getOrDefault(certificate.getAccountId(), "unassigned"));
            }
            try {
                certificateRepositoryPort.save(certificate);
                persisted++;
            } catch (RuntimeException e) {
                // One certificate the database rejects must not discard the rest.
                log.error("Could not persist certificate {} ({}): {}",
                        certificate.getDomain(), certificate.getResource(), rootMessage(e));
            }
        }

        scan.setCertificatesDiscovered(persisted);
        scan.setAccountsCompleted(Math.min(accountCount, accountsWithOutcome.size()));
        scan.setProgressPercent(100);

        // An unrecognised service name is a configuration error worth surfacing,
        // so it counts against the scan even though no task ran for it.
        boolean anyFailure = failures.get() > 0 || !plan.unknownServices().isEmpty();
        boolean anySuccess = successes.get() > 0;

        if (anyFailure && anySuccess) {
            scan.transitionTo(ScanState.PARTIAL_SUCCESS);
        } else if (anyFailure) {
            scan.transitionTo(ScanState.FAILED);
        } else {
            scan.transitionTo(ScanState.COMPLETED);
        }

        log.info("Scan {} finished: {} certificate(s), {} task(s) ok, {} failed -> {}",
                scan.getId(), persisted, successes.get(), failures.get(), scan.getStatus());
        scanRepositoryPort.save(scan);
    }

    private void failScan(Scan scan) {
        try {
            scan.transitionTo(ScanState.FAILED);
        } catch (IllegalStateException alreadyTerminal) {
            scan.setStatus(ScanState.FAILED);
        }
        scanRepositoryPort.save(scan);
    }

    private static ThreadFactory namedDaemonThreads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
