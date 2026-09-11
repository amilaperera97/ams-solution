package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.application.port.AccountRepositoryPort;
import uk.co.ams.certplatform.application.port.CertificateDiscoveryStrategy;
import uk.co.ams.certplatform.application.port.CertificateRepositoryPort;
import uk.co.ams.certplatform.application.port.EnvironmentRepositoryPort;
import uk.co.ams.certplatform.application.port.ProviderRepositoryPort;
import uk.co.ams.certplatform.application.port.ScanRepositoryPort;
import uk.co.ams.certplatform.domain.enums.AccountAuthType;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.enums.DiscoveryStatus;
import uk.co.ams.certplatform.domain.enums.ScanScopeType;
import uk.co.ams.certplatform.domain.enums.ScanState;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.domain.model.DiscoveryResult;
import uk.co.ams.certplatform.domain.model.DiscoveryServiceDescriptor;
import uk.co.ams.certplatform.domain.model.Provider;
import uk.co.ams.certplatform.domain.model.Scan;
import uk.co.ams.certplatform.domain.model.ScanContext;
import uk.co.ams.certplatform.infrastructure.ratelimit.AccountRateLimiters;
import uk.co.ams.certplatform.shared.config.DiscoveryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ScanExecutorParallelTest {

    @Mock private ScanRepositoryPort scanRepositoryPort;
    @Mock private AccountRepositoryPort accountRepositoryPort;
    @Mock private ProviderRepositoryPort providerRepositoryPort;
    @Mock private EnvironmentRepositoryPort environmentRepositoryPort;
    @Mock private CertificateRepositoryPort certificateRepositoryPort;
    @Mock private CertificateDiscoveryStrategy acmStrategy;
    @Mock private CertificateDiscoveryStrategy albStrategy;

    private DefaultScanExecutor executor;
    private Account account;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        when(acmStrategy.descriptor()).thenReturn(
                DiscoveryServiceDescriptor.builder(CloudProviderType.AWS, "ACM", "ACM").build());
        when(albStrategy.descriptor()).thenReturn(
                DiscoveryServiceDescriptor.builder(CloudProviderType.AWS, "ALB", "ALB").build());

        CertificateDiscoveryStrategyRegistry registry =
                new CertificateDiscoveryStrategyRegistry(List.of(acmStrategy, albStrategy));

        DiscoveryProperties properties = new DiscoveryProperties();
        properties.getParallelism().setMaxConcurrentTasks(8);
        properties.getParallelism().setMaxConcurrentTasksPerAccount(8);
        properties.getParallelism().setTaskStartsPerSecondPerAccount(1000);

        executor = new DefaultScanExecutor(scanRepositoryPort, accountRepositoryPort, providerRepositoryPort,
                environmentRepositoryPort, registry, new DiscoveryTaskPlanner(registry),
                new CertificateIdentityResolver(), certificateRepositoryPort,
                new AccountRateLimiters(properties), properties);

        account = Account.builder()
                .id("acc-1")
                .providerId("prov-1")
                .region("eu-west-2")
                .authType(AccountAuthType.ACCESS_KEY)
                .build();
        when(accountRepositoryPort.findById("acc-1")).thenReturn(Optional.of(account));
        when(providerRepositoryPort.findById("prov-1")).thenReturn(Optional.of(
                new Provider("prov-1", "AWS", "org-1", CloudProviderType.AWS, "ACTIVE", null, null)));
    }

    @Test
    void runsTheRegionsOfAServiceConcurrently() throws InterruptedException {
        scan("scan-1", Arrays.asList("eu-west-1", "eu-west-2"), Collections.singletonList("ACM"));
        CountDownLatch bothStarted = new CountDownLatch(2);

        when(acmStrategy.discover(any(ScanContext.class))).thenAnswer(invocation -> {
            bothStarted.countDown();
            // Neither call can return until the other has started, so a sequential
            // executor would deadlock here rather than merely being slow.
            assertTrue(bothStarted.await(5, TimeUnit.SECONDS), "regions were not scanned concurrently");
            return DiscoveryResult.accumulator().build();
        });

        executor.executeScan("scan-1");

        verify(acmStrategy, times(2)).discover(any(ScanContext.class));
        assertEquals(ScanState.COMPLETED, savedScan().status());
    }

    @Test
    void reportsPartialSuccessWhenOneServiceFailsAndAnotherWorks() {
        scan("scan-2", Collections.singletonList("eu-west-1"), Arrays.asList("ACM", "ALB"));

        when(acmStrategy.discover(any())).thenReturn(DiscoveryResult.accumulator().succeeded("ok"));
        when(albStrategy.discover(any())).thenThrow(new RuntimeException("ELB API down"));

        executor.executeScan("scan-2");

        assertEquals(ScanState.PARTIAL_SUCCESS, savedScan().status());
    }

    @Test
    void treatsADeniedServiceAsSkippedRatherThanAFailedScan() {
        scan("scan-3", Collections.singletonList("eu-west-1"), Arrays.asList("ACM", "ALB"));

        when(acmStrategy.discover(any())).thenReturn(DiscoveryResult.accumulator().succeeded("ok"));
        when(albStrategy.discover(any())).thenReturn(DiscoveryResult.accumulator().skipped("not permitted"));

        executor.executeScan("scan-3");

        assertEquals(ScanState.COMPLETED, savedScan().status(),
                "a service the credentials cannot read is a coverage gap, not a broken scan");
    }

    @Test
    void failsTheScanWhenAServiceNameMatchesNothing() {
        scan("scan-4", Collections.singletonList("eu-west-1"), Arrays.asList("ACM", "NOT_A_SERVICE"));
        when(acmStrategy.discover(any())).thenReturn(DiscoveryResult.accumulator().succeeded("ok"));

        executor.executeScan("scan-4");

        assertEquals(ScanState.PARTIAL_SUCCESS, savedScan().status(),
                "a service name nothing claims is a configuration error worth surfacing");
    }

    @Test
    void keepsCertificatesFromAPartialResult() {
        scan("scan-5", Collections.singletonList("eu-west-1"), Collections.singletonList("ACM"));

        DiscoveryResult throttled = DiscoveryResult.accumulator()
                .addCertificate(certificate("a.example.com"))
                .partial("throttled");
        when(acmStrategy.discover(any())).thenReturn(throttled);

        executor.executeScan("scan-5");

        verify(certificateRepositoryPort, times(1)).save(any());
        assertEquals(1, savedScan().certificatesDiscovered());
    }

    @Test
    void skipsAServiceTurnedOffByConfiguration() {
        DiscoveryProperties properties = new DiscoveryProperties();
        properties.setDisabledServices(List.of("AWS:ALB"));
        CertificateDiscoveryStrategyRegistry registry =
                new CertificateDiscoveryStrategyRegistry(List.of(acmStrategy, albStrategy));
        DefaultScanExecutor configured = new DefaultScanExecutor(scanRepositoryPort, accountRepositoryPort,
                providerRepositoryPort, environmentRepositoryPort, registry, new DiscoveryTaskPlanner(registry),
                new CertificateIdentityResolver(), certificateRepositoryPort,
                new AccountRateLimiters(properties), properties);

        scan("scan-6", Collections.singletonList("eu-west-1"), Arrays.asList("ACM", "ALB"));
        when(acmStrategy.discover(any())).thenReturn(DiscoveryResult.accumulator().succeeded("ok"));

        configured.executeScan("scan-6");

        verify(albStrategy, never()).discover(any());
        assertEquals(ScanState.COMPLETED, savedScan().status());
    }

    @Test
    void dispatchesUsingTheProviderTheAccountBelongsTo() {
        when(accountRepositoryPort.findById("acc-1"))
                .thenReturn(Optional.of(account.toBuilder().providerId("prov-azure").build()));
        when(providerRepositoryPort.findById("prov-azure")).thenReturn(Optional.of(
                new Provider("prov-azure", "Azure", "org-1", CloudProviderType.AZURE, "ACTIVE", null, null)));

        scan("scan-7", Collections.singletonList("eu-west-1"), Collections.singletonList("ACM"));
        executor.executeScan("scan-7");

        // ACM is an AWS service; an Azure account must not be handed to it.
        verify(acmStrategy, never()).discover(any());
    }

    private Scan scan(String id, List<String> regions, List<String> services) {
        Scan scan = Scan.builder()
                .id(id)
                .scopeType(ScanScopeType.ACCOUNT)
                .accountIds(Collections.singletonList("acc-1"))
                .regions(regions)
                .services(services)
                .build();
        when(scanRepositoryPort.findById(id)).thenReturn(Optional.of(scan));
        return scan;
    }

    private Scan savedScan() {
        ArgumentCaptor<Scan> captor = ArgumentCaptor.forClass(Scan.class);
        verify(scanRepositoryPort, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private static uk.co.ams.certplatform.domain.model.Certificate certificate(String domain) {
        return uk.co.ams.certplatform.domain.model.Certificate.builder()
                .domain(domain)
                .fingerprint("AA:BB:" + domain)
                .build();
    }

    @Test
    void doesNothingWhenTheScanHasBeenDeleted() {
        when(scanRepositoryPort.findById("gone")).thenReturn(Optional.empty());

        executor.executeScan("gone");

        verify(scanRepositoryPort, never()).save(any());
        assertEquals(DiscoveryStatus.SUCCESS, DiscoveryResult.accumulator().build().status());
    }
}
