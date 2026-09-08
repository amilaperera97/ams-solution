package uk.co.ams.certplatform.infrastructure.discovery.aws;

import uk.co.ams.certplatform.domain.enums.AccountAuthType;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.enums.ProviderMode;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.domain.model.ConnectionTestResult;
import uk.co.ams.certplatform.domain.model.DiscoveryResult;
import uk.co.ams.certplatform.domain.model.ScanContext;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsClientFactory;
import uk.co.ams.certplatform.infrastructure.cloud.azure.AzureCloudProviderAdapter;
import uk.co.ams.certplatform.infrastructure.cloud.gcp.GcpCloudProviderAdapter;
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
        Account account = new Account();
        account.setId("acc-real");
        account.setAccountId("123456789012");
        account.setAuthType(AccountAuthType.ACCESS_KEY);
        account.setAccessKeyId("AKIAIOSFODNN7EXAMPLE");
        account.setSecretAccessKey("wJalrXUtnFEMI/K7MDENG");
        account.setRegion("eu-west-2");
        return account;
    }

    @Test
    void acmDiscoveryShouldCallAwsRatherThanFabricateCertificates() {
        CloudProviderProperties properties = realAwsProperties();
        AwsAcmCertificateDiscoveryStrategy strategy =
                new AwsAcmCertificateDiscoveryStrategy(properties, new AwsClientFactory(properties));

        DiscoveryResult result = strategy.discover(new ScanContext("s1", realAccount(), "eu-west-2", "ACM"));

        assertEquals("FAILED", result.getStatus());
        assertTrue(result.getCertificates().isEmpty(), "REAL mode must not invent certificates");
        assertFalse(result.getErrors().isEmpty(), "the AWS failure should be reported");
    }

    @Test
    void ec2DiscoveryShouldBeSkippedRatherThanSimulatedInRealMode() {
        AwsEc2CertificateDiscoveryStrategy strategy =
                new AwsEc2CertificateDiscoveryStrategy(List.of(), List.of(), realAwsProperties());

        DiscoveryResult result = strategy.discover(new ScanContext("s1", realAccount(), "eu-west-2", "EC2"));

        assertEquals("SKIPPED", result.getStatus());
        assertTrue(result.getCertificates().isEmpty());
        assertTrue(result.getMessage().contains("not implemented for REAL mode"));
    }

    @Test
    void secretsManagerDiscoveryShouldBeSkippedInRealMode() {
        AwsSecretsManagerCertificateDiscoveryStrategy strategy =
                new AwsSecretsManagerCertificateDiscoveryStrategy(realAwsProperties());

        DiscoveryResult result = strategy.discover(
                new ScanContext("s1", realAccount(), "eu-west-2", "SECRETS_MANAGER"));

        assertEquals("SKIPPED", result.getStatus());
        assertTrue(result.getCertificates().isEmpty());
    }

    @Test
    void azureAndGcpShouldNotReportASimulatedSuccessInRealMode() {
        CloudProviderProperties.ProviderSettings real = new CloudProviderProperties.ProviderSettings();
        real.setMode(ProviderMode.REAL);
        CloudProviderProperties properties = new CloudProviderProperties();
        properties.setProviders(Map.of("azure", real, "gcp", real));

        Account account = new Account();
        account.setAccountId("sub-123");
        account.setToken("a-token-that-would-pass-in-mock-mode");

        ConnectionTestResult azure = new AzureCloudProviderAdapter(properties).testConnection(account);
        ConnectionTestResult gcp = new GcpCloudProviderAdapter(properties).testConnection(account);

        assertEquals("FAILED", azure.getStatus());
        assertEquals("FAILED", gcp.getStatus());
        assertTrue(azure.getMessage().contains("not implemented"));
        assertTrue(gcp.getMessage().contains("not implemented"));
    }

    @Test
    void mockModeShouldStillSimulate() {
        // Empty properties leave every provider in MOCK.
        CloudProviderProperties properties = new CloudProviderProperties();
        assertFalse(properties.isReal(CloudProviderType.AWS));

        AwsAcmCertificateDiscoveryStrategy strategy =
                new AwsAcmCertificateDiscoveryStrategy(properties, new AwsClientFactory(properties));
        DiscoveryResult result = strategy.discover(new ScanContext("s1", realAccount(), "eu-west-2", "ACM"));

        assertEquals("SUCCESS", result.getStatus());
        assertEquals(1, result.getCertificates().size());
        assertEquals("acm.example.com", result.getCertificates().get(0).getDomain());
    }
}
