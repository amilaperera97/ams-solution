package uk.co.ams.certplatform.infrastructure.discovery.aws;

import uk.co.ams.certplatform.domain.enums.AccountAuthType;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.enums.DiscoveryStatus;
import uk.co.ams.certplatform.domain.enums.ProviderMode;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.domain.model.ConnectionTestResult;
import uk.co.ams.certplatform.domain.model.DiscoveryResult;
import uk.co.ams.certplatform.domain.model.ScanContext;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsCertificateReferenceResolver;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsClientFactory;
import uk.co.ams.certplatform.infrastructure.cloud.azure.AzureCloudProviderAdapter;
import uk.co.ams.certplatform.infrastructure.cloud.gcp.GcpCloudProviderAdapter;
import uk.co.ams.certplatform.infrastructure.compute.ComputeCertificateScanner;
import uk.co.ams.certplatform.infrastructure.discovery.aws.phase2.AwsSecretsManagerDiscoveryStrategy;
import uk.co.ams.certplatform.infrastructure.discovery.support.SimulatedCertificateFactory;
import uk.co.ams.certplatform.infrastructure.discovery.support.X509CertificateParser;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REAL mode must never hand back invented data. These tests point the SDK at a
 * dead local endpoint, so they prove a network call was attempted without needing
 * real AWS credentials or internet access.
 */
class RealModeGuardsTest {

    private static final String DEAD_ENDPOINT = "http://127.0.0.1:1";

    private final SimulatedCertificateFactory simulator = new SimulatedCertificateFactory();

    private CloudProviderProperties realAwsProperties() {
        CloudProviderProperties.ProviderSettings aws = new CloudProviderProperties.ProviderSettings();
        aws.setMode(ProviderMode.REAL);
        aws.setEndpoint(DEAD_ENDPOINT);
        aws.setApiTimeoutSeconds(2);
        aws.setDefaultRegion("eu-west-2");
        CloudProviderProperties properties = new CloudProviderProperties();
        properties.setProviders(Map.of("aws", aws));
        return properties;
    }

    private Account realAccount() {
        return Account.builder()
                .id("acc-real")
                .accountId("123456789012")
                .authType(AccountAuthType.ACCESS_KEY)
                .accessKeyId("AKIAIOSFODNN7EXAMPLE")
                .secretAccessKey("wJalrXUtnFEMI/K7MDENG")
                .region("eu-west-2")
                .build();
    }

    private AwsAcmDiscoveryStrategy acmStrategy(CloudProviderProperties properties) {
        AwsClientFactory clientFactory = new AwsClientFactory(properties);
        return new AwsAcmDiscoveryStrategy(properties, simulator, clientFactory,
                new AwsCertificateReferenceResolver(clientFactory, new X509CertificateParser()));
    }

    @Test
    void acmDiscoveryShouldCallAwsRatherThanFabricateCertificates() {
        DiscoveryResult result = acmStrategy(realAwsProperties())
                .discover(new ScanContext("s1", realAccount(), "eu-west-2", "ACM"));

        assertEquals(DiscoveryStatus.FAILED, result.status());
        assertTrue(result.certificates().isEmpty(), "REAL mode must not invent certificates");
        assertFalse(result.errors().isEmpty(), "the AWS failure should be reported");
    }

    @Test
    void ec2DiscoveryShouldReportNoReachableInstancesRatherThanSimulatedOnes() {
        CloudProviderProperties properties = realAwsProperties();
        AwsClientFactory clientFactory = new AwsClientFactory(properties);
        AwsEc2FilesystemDiscoveryStrategy strategy = new AwsEc2FilesystemDiscoveryStrategy(
                properties, simulator, clientFactory,
                new ComputeCertificateScanner(List.of(), List.of()));

        DiscoveryResult result = strategy.discover(new ScanContext("s1", realAccount(), "eu-west-2", "EC2"));

        // No compute executor is wired in this unit test, so the scanner reports the
        // gap explicitly. What matters is that nothing was invented.
        assertEquals(DiscoveryStatus.NOT_IMPLEMENTED, result.status());
        assertTrue(result.certificates().isEmpty());
    }

    @Test
    void aRegisteredButUnbuiltServiceReportsNotImplementedInEitherMode() {
        for (CloudProviderProperties properties : List.of(realAwsProperties(), new CloudProviderProperties())) {
            AwsSecretsManagerDiscoveryStrategy strategy = new AwsSecretsManagerDiscoveryStrategy(
                    properties, simulator, new AwsClientFactory(properties));

            DiscoveryResult result = strategy.discover(
                    new ScanContext("s1", realAccount(), "eu-west-2", "SECRETS_MANAGER"));

            assertEquals(DiscoveryStatus.NOT_IMPLEMENTED, result.status());
            assertTrue(result.certificates().isEmpty());
        }
    }

    @Test
    void azureAndGcpShouldNotReportASimulatedSuccessInRealMode() {
        CloudProviderProperties.ProviderSettings real = new CloudProviderProperties.ProviderSettings();
        real.setMode(ProviderMode.REAL);
        CloudProviderProperties properties = new CloudProviderProperties();
        properties.setProviders(Map.of("azure", real, "gcp", real));

        Account account = Account.builder()
                .accountId("sub-123")
                .token("a-token-that-would-pass-in-mock-mode")
                .build();

        ConnectionTestResult azure = new AzureCloudProviderAdapter(properties).testConnection(account);
        ConnectionTestResult gcp = new GcpCloudProviderAdapter(properties).testConnection(account);

        assertEquals("FAILED", azure.status());
        assertEquals("FAILED", gcp.status());
        assertTrue(azure.message().contains("not implemented"));
        assertTrue(gcp.message().contains("not implemented"));
    }

    @Test
    void mockModeShouldStillSimulate() {
        // Empty properties leave every provider in MOCK.
        CloudProviderProperties properties = new CloudProviderProperties();
        assertFalse(properties.isReal(CloudProviderType.AWS));

        DiscoveryResult result = acmStrategy(properties)
                .discover(new ScanContext("s1", realAccount(), "eu-west-2", "ACM"));

        assertEquals(DiscoveryStatus.SUCCESS, result.status());
        assertEquals(1, result.certificates().size());
        assertEquals("acm.example.com", result.certificates().get(0).domain());
    }

    @Test
    void everyPhaseOneServiceCallsAwsInsteadOfSimulatingWhenModeIsReal() {
        CloudProviderProperties properties = realAwsProperties();
        AwsClientFactory clientFactory = new AwsClientFactory(properties);
        AwsCertificateReferenceResolver resolver =
                new AwsCertificateReferenceResolver(clientFactory, new X509CertificateParser());

        List<uk.co.ams.certplatform.application.port.CertificateDiscoveryStrategy> strategies = List.of(
                new AwsAlbDiscoveryStrategy(properties, simulator, clientFactory, resolver),
                new AwsNlbDiscoveryStrategy(properties, simulator, clientFactory, resolver),
                new AwsCloudFrontDiscoveryStrategy(properties, simulator, clientFactory, resolver),
                new AwsApiGatewayDiscoveryStrategy(properties, simulator, clientFactory, resolver));

        for (var strategy : strategies) {
            DiscoveryResult result = strategy.discover(
                    new ScanContext("s1", realAccount(), "eu-west-2", strategy.descriptor().key()));

            assertTrue(result.certificates().isEmpty(),
                    strategy.descriptor().key() + " invented certificates in REAL mode");
            assertNotEquals(DiscoveryStatus.SUCCESS, result.status(),
                    strategy.descriptor().key() + " reported success without reaching AWS");
        }
    }
}
