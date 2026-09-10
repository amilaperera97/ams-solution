package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.application.port.CertificateDiscoveryStrategy;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.DiscoveryServiceDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CertificateDiscoveryStrategyRegistryTest {

    private CertificateDiscoveryStrategyRegistry registry;
    private CertificateDiscoveryStrategy awsCloudFront;

    @BeforeEach
    void setUp() {
        registry = new CertificateDiscoveryStrategyRegistry(Arrays.asList(
                strategyFor(descriptor(CloudProviderType.AWS, "EC2", "EC2 instance filesystems")
                        .aliases("EC2_FILESYSTEM").build()),
                strategyFor(descriptor(CloudProviderType.AWS, "ACM", "Certificate Manager").build()),
                awsCloudFront = strategyFor(descriptor(CloudProviderType.AWS, "CLOUDFRONT", "CloudFront")
                        .global("us-east-1").build()),
                strategyFor(descriptor(CloudProviderType.AWS, "S3", "S3 objects")
                        .phase(2).notImplemented().build()),
                strategyFor(descriptor(CloudProviderType.AZURE, "VM", "Virtual Machines").build())));
    }

    @Test
    void listsEveryServiceRegisteredForAProvider() {
        List<DiscoveryServiceDescriptor> aws = registry.descriptorsFor(CloudProviderType.AWS);

        assertEquals(4, aws.size());
        assertTrue(aws.stream().anyMatch(d -> d.key().equals("EC2")));
        assertTrue(aws.stream().anyMatch(d -> d.key().equals("ACM")));
    }

    @Test
    void separatesServicesThatCanActuallyRunFromRegisteredPlaceholders() {
        List<DiscoveryServiceDescriptor> implemented = registry.implementedDescriptorsFor(CloudProviderType.AWS);

        assertEquals(3, implemented.size());
        assertTrue(implemented.stream().noneMatch(d -> d.key().equals("S3")),
                "a registered-but-unbuilt service must not be scanned by default");
    }

    @Test
    void resolvesAServiceByCanonicalKeyIgnoringCase() {
        assertTrue(registry.resolve(CloudProviderType.AWS, "acm").isPresent());
        assertTrue(registry.resolve(CloudProviderType.AWS, " ACM ").isPresent());
    }

    @Test
    void resolvesAServiceByAliasSoOlderSavedScansKeepWorking() {
        assertEquals("EC2", registry.resolve(CloudProviderType.AWS, "EC2_FILESYSTEM").orElseThrow().key());
    }

    @Test
    void doesNotResolveAServiceBelongingToADifferentProvider() {
        assertTrue(registry.resolve(CloudProviderType.AWS, "VM").isEmpty());
        assertTrue(registry.resolve(CloudProviderType.GCP, "ACM").isEmpty());
    }

    @Test
    void returnsTheStrategyBehindADescriptor() {
        DiscoveryServiceDescriptor cloudFront =
                registry.resolve(CloudProviderType.AWS, "CLOUDFRONT").orElseThrow();

        assertSame(awsCloudFront, registry.strategyFor(cloudFront).orElseThrow());
    }

    @Test
    void hasNothingForAProviderWithNoStrategies() {
        assertTrue(registry.descriptorsFor(CloudProviderType.GCP).isEmpty());
    }

    @Test
    void refusesToStartWhenTwoStrategiesClaimTheSameService() {
        List<CertificateDiscoveryStrategy> clashing = Arrays.asList(
                strategyFor(descriptor(CloudProviderType.AWS, "ACM", "Certificate Manager").build()),
                strategyFor(descriptor(CloudProviderType.AWS, "ACM", "Certificate Manager again").build()));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new CertificateDiscoveryStrategyRegistry(clashing));
        assertTrue(failure.getMessage().contains("AWS:ACM"));
    }

    private static DiscoveryServiceDescriptor.Builder descriptor(CloudProviderType provider,
                                                                 String key, String label) {
        return DiscoveryServiceDescriptor.builder(provider, key, label);
    }

    private static CertificateDiscoveryStrategy strategyFor(DiscoveryServiceDescriptor descriptor) {
        CertificateDiscoveryStrategy strategy = mock(CertificateDiscoveryStrategy.class);
        when(strategy.descriptor()).thenReturn(descriptor);
        return strategy;
    }
}
