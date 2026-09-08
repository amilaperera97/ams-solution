package com.example.certplatform.application.service;

import com.example.certplatform.application.port.AccountRepositoryPort;
import com.example.certplatform.application.port.CertificateDiscoveryStrategy;
import com.example.certplatform.application.port.CertificateRepositoryPort;
import com.example.certplatform.application.port.ScanRepositoryPort;
import com.example.certplatform.domain.enums.CloudProviderType;
import com.example.certplatform.domain.enums.ScanState;
import com.example.certplatform.domain.model.Account;
import com.example.certplatform.domain.model.DiscoveryResult;
import com.example.certplatform.domain.model.Scan;
import com.example.certplatform.domain.model.ScanContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ScanExecutorParallelTest {

    @Mock private ScanRepositoryPort scanRepositoryPort;
    @Mock private AccountRepositoryPort accountRepositoryPort;
    @Mock private CertificateDiscoveryStrategyRegistry strategyRegistry;
    @Mock private CertificateIdentityResolver identityResolver;
    @Mock private CertificateRepositoryPort certificateRepositoryPort;
    @Mock private CertificateDiscoveryStrategy acmStrategy;
    @Mock private CertificateDiscoveryStrategy ec2Strategy;

    @InjectMocks
    private DefaultScanExecutor executor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(executor, "maxParallelRegions", 5);
        ReflectionTestUtils.setField(executor, "maxParallelServices", 10);
    }

    @Test
    void shouldExecuteRegionsConcurrently() throws InterruptedException {
        Scan scan = new Scan();
        scan.setId("scan-1");
        scan.setScopeType(com.example.certplatform.domain.enums.ScanScopeType.ACCOUNT);
        scan.setAccountIds(Collections.singletonList("acc-1"));
        scan.setRegions(Arrays.asList("eu-west-1", "eu-west-2"));
        scan.setServices(Collections.singletonList("ACM"));

        when(scanRepositoryPort.findById("scan-1")).thenReturn(Optional.of(scan));
        
        Account account = new Account();
        account.setId("acc-1");
        account.setProviderId("prov-1");
        account.setAuthType(com.example.certplatform.domain.enums.AccountAuthType.TOKEN);
        when(accountRepositoryPort.findById("acc-1")).thenReturn(Optional.of(account));
        
        // Mock registry to return ACM strategy
        when(strategyRegistry.findStrategies(any(), eq("ACM"), any())).thenReturn(Collections.singletonList(acmStrategy));
        
        CountDownLatch latch = new CountDownLatch(2);
        
        // Make the strategy delay to test concurrency
        when(acmStrategy.discover(any(ScanContext.class))).thenAnswer(inv -> {
            latch.countDown();
            // Wait for both to start, proving concurrency
            latch.await(2, TimeUnit.SECONDS); 
            return new DiscoveryResult();
        });

        long start = System.currentTimeMillis();
        executor.executeScan("scan-1");
        long end = System.currentTimeMillis();

        verify(acmStrategy, times(2)).discover(any(ScanContext.class));
        assertTrue((end - start) < 2000, "Should execute concurrently");
        assertEquals(ScanState.COMPLETED, scan.getStatus());
    }

    @Test
    void shouldHandlePartialFailure() {
        Scan scan = new Scan();
        scan.setId("scan-2");
        scan.setScopeType(com.example.certplatform.domain.enums.ScanScopeType.ACCOUNT);
        scan.setAccountIds(Collections.singletonList("acc-1"));
        scan.setRegions(Collections.singletonList("eu-west-1"));
        scan.setServices(Arrays.asList("ACM", "EC2"));

        when(scanRepositoryPort.findById("scan-2")).thenReturn(Optional.of(scan));
        Account account = new Account();
        account.setId("acc-1");
        when(accountRepositoryPort.findById("acc-1")).thenReturn(Optional.of(account));
        
        when(strategyRegistry.findStrategies(any(), eq("ACM"), any())).thenReturn(Collections.singletonList(acmStrategy));
        when(strategyRegistry.findStrategies(any(), eq("EC2"), any())).thenReturn(Collections.singletonList(ec2Strategy));
        
        // ACM succeeds
        DiscoveryResult acmResult = new DiscoveryResult();
        acmResult.setStatus("SUCCESS");
        when(acmStrategy.discover(any())).thenReturn(acmResult);
        
        // EC2 throws exception
        when(ec2Strategy.discover(any())).thenThrow(new RuntimeException("EC2 API down"));

        executor.executeScan("scan-2");

        ArgumentCaptor<Scan> scanCaptor = ArgumentCaptor.forClass(Scan.class);
        verify(scanRepositoryPort, atLeastOnce()).save(scanCaptor.capture());
        
        Scan savedScan = scanCaptor.getValue();
        assertEquals(ScanState.PARTIAL_SUCCESS, savedScan.getStatus());
    }
}
