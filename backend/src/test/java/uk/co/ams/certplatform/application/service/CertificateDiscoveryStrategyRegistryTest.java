package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.application.port.CertificateDiscoveryStrategy;
import uk.co.ams.certplatform.application.port.DiscoveryCapability;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CertificateDiscoveryStrategyRegistryTest {

    private CertificateDiscoveryStrategyRegistry registry;

    @BeforeEach
    void setUp() {
        CertificateDiscoveryStrategy awsEc2Strategy = mock(CertificateDiscoveryStrategy.class);
        when(awsEc2Strategy.capability()).thenReturn(new DiscoveryCapability(CloudProviderType.AWS, "EC2", "AwsEc2Discovery"));

        CertificateDiscoveryStrategy awsAcmStrategy = mock(CertificateDiscoveryStrategy.class);
        when(awsAcmStrategy.capability()).thenReturn(new DiscoveryCapability(CloudProviderType.AWS, "ACM", "AwsAcmDiscovery"));

        CertificateDiscoveryStrategy azureVmStrategy = mock(CertificateDiscoveryStrategy.class);
        when(azureVmStrategy.capability()).thenReturn(new DiscoveryCapability(CloudProviderType.AZURE, "VM", "AzureVmDiscovery"));

        List<CertificateDiscoveryStrategy> strategies = Arrays.asList(awsEc2Strategy, awsAcmStrategy, azureVmStrategy);
        registry = new CertificateDiscoveryStrategyRegistry(strategies);
    }

    @Test
    void shouldFindStrategiesByProvider() {
        List<CertificateDiscoveryStrategy> awsStrategies = registry.getAllSupportedStrategies(CloudProviderType.AWS);
        assertEquals(2, awsStrategies.size());
        assertTrue(awsStrategies.stream().anyMatch(s -> s.capability().getService().equals("EC2")));
        assertTrue(awsStrategies.stream().anyMatch(s -> s.capability().getService().equals("ACM")));
    }

    @Test
    void shouldFindStrategyByProviderAndService() {
        List<CertificateDiscoveryStrategy> strategy = registry.findStrategies(CloudProviderType.AWS, "EC2", null);
        assertEquals(1, strategy.size());
        assertEquals("AwsEc2Discovery", strategy.get(0).capability().getCapabilityName());
    }

    @Test
    void shouldReturnEmptyWhenNoProviderStrategyMatches() {
        List<CertificateDiscoveryStrategy> gcpStrategies = registry.getAllSupportedStrategies(CloudProviderType.GCP);
        assertTrue(gcpStrategies.isEmpty());
    }
}
