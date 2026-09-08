package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.application.port.ScanJobPublisher;
import uk.co.ams.certplatform.application.port.ScanRepositoryPort;
import uk.co.ams.certplatform.domain.enums.ScanScopeType;
import uk.co.ams.certplatform.domain.enums.ScanState;
import uk.co.ams.certplatform.domain.model.Scan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ScanServiceTest {

    @Mock
    private ScanRepositoryPort scanRepositoryPort;

    @Mock
    private ScanJobPublisher scanJobPublisher;

    @InjectMocks
    private ScanService scanService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldCreateAndQueueScan() {
        Scan savedScan = new Scan();
        savedScan.setId("scan-123");
        savedScan.setStatus(ScanState.REQUESTED);

        when(scanRepositoryPort.save(any(Scan.class))).thenAnswer(invocation -> {
            Scan arg = invocation.getArgument(0);
            if (arg.getId() == null) arg.setId("scan-123");
            return arg;
        });

        Scan result = scanService.createScan("Test Scan", ScanScopeType.ACCOUNT, null, null, Collections.singletonList("acc-123"), null, null);

        assertNotNull(result);
        assertEquals(ScanState.QUEUED, result.getStatus());
        verify(scanJobPublisher, times(1)).publish(anyString());
        // Save is called twice: once for initial save, once after state transition to QUEUED
        verify(scanRepositoryPort, times(2)).save(any(Scan.class));
    }

    @Test
    void shouldThrowExceptionWhenAccountIdsMissingForAccountScope() {
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            scanService.createScan("Test Scan", ScanScopeType.ACCOUNT, null, null, null, null, null);
        });

        assertTrue(exception.getMessage().contains("Account IDs are required"));
        verify(scanRepositoryPort, never()).save(any());
    }
}
