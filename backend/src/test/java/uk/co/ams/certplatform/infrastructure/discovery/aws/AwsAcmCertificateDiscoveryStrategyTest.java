package uk.co.ams.certplatform.infrastructure.discovery.aws;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.domain.model.DiscoveryResult;
import uk.co.ams.certplatform.domain.model.ScanContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AwsAcmCertificateDiscoveryStrategyTest {

    private AwsAcmCertificateDiscoveryStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new AwsAcmCertificateDiscoveryStrategy();
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
        // Since we are mocking the provider, we simulate the discovery result for ACM
        ScanContext ctx = new ScanContext("s1", new Account(), "eu-west-1", "ACM");
        DiscoveryResult result = strategy.discover(ctx);
        
        assertNotNull(result);
        assertEquals("SUCCESS", result.getStatus());
        // For testing purposes, we might just assert that the mock returns empty initially
        // Once WireMock client is wired up, we would assert the actual result
    }
}
