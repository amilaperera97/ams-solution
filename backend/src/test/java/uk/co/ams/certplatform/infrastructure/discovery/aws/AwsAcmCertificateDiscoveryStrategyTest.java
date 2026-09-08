package uk.co.ams.certplatform.infrastructure.discovery.aws;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.domain.model.DiscoveryResult;
import uk.co.ams.certplatform.domain.model.ScanContext;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsClientFactory;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AwsAcmCertificateDiscoveryStrategyTest {

    private AwsAcmCertificateDiscoveryStrategy strategy;

    @BeforeEach
    void setUp() {
        // Default properties leave AWS in MOCK, so discover() takes the simulated path
        // and never touches the network.
        CloudProviderProperties properties = new CloudProviderProperties();
        strategy = new AwsAcmCertificateDiscoveryStrategy(properties, new AwsClientFactory(properties));
    }

    @Test
    void shouldReturnCorrectCapability() {
        var capability = strategy.capability();
        assertEquals(CloudProviderType.AWS, capability.getProvider());
        assertEquals("ACM", capability.getService());
    }

    @Test
    void shouldSupportAwsAcmContext() {
        ScanContext ctx = new ScanContext("s1", new Account(), "eu-west-1", "ACM");
        assertTrue(strategy.supports(ctx));
    }
    
    @Test
    void shouldNotSupportOtherContexts() {
        ScanContext ctx = new ScanContext("s1", new Account(), "eu-west-1", "EC2");
        assertFalse(strategy.supports(ctx));
    }

    @Test
    void shouldDiscoverCertificates() {
        // Provider mode is MOCK here, so this exercises the simulated ACM path.
        ScanContext ctx = new ScanContext("s1", new Account(), "eu-west-1", "ACM");
        DiscoveryResult result = strategy.discover(ctx);
        
        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        assertEquals(1, result.getCertificates().size());
    }

    @Test
    void shouldMapAcmKeyAlgorithmsToKeySizes() {
        assertEquals(2048, AwsAcmCertificateDiscoveryStrategy.keySizeOf("RSA_2048"));
        assertEquals(4096, AwsAcmCertificateDiscoveryStrategy.keySizeOf("RSA_4096"));
        assertEquals(256, AwsAcmCertificateDiscoveryStrategy.keySizeOf("EC_prime256v1"));
        assertEquals(384, AwsAcmCertificateDiscoveryStrategy.keySizeOf("EC_secp384r1"));
        assertNull(AwsAcmCertificateDiscoveryStrategy.keySizeOf("SOMETHING_NEW"));
        assertNull(AwsAcmCertificateDiscoveryStrategy.keySizeOf(null));
    }
}
