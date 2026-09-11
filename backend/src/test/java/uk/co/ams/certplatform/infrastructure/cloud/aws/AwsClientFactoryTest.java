package uk.co.ams.certplatform.infrastructure.cloud.aws;

import uk.co.ams.certplatform.domain.enums.AccountAuthType;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

import static org.junit.jupiter.api.Assertions.*;

class AwsClientFactoryTest {

    private final CloudProviderProperties properties = new CloudProviderProperties();
    private final AwsClientFactory factory = new AwsClientFactory(properties);

    private Account accessKeyAccount() {
        return Account.builder()
                .id("acc-1")
                .authType(AccountAuthType.ACCESS_KEY)
                .accessKeyId("AKIAIOSFODNN7EXAMPLE")
                .secretAccessKey("wJalrXUtnFEMI/K7MDENG")
                .region("eu-west-2")
                .build();
    }

    private Account iamRoleAccount() {
        return Account.builder()
                .id("acc-role")
                .authType(AccountAuthType.IAM_ROLE)
                .roleArn("arn:aws:iam::123456789012:role/CertificateDiscoveryRole")
                .region("eu-west-2")
                .build();
    }

    @Test
    void shouldBuildStaticCredentialsFromStoredAccessKey() {
        AwsCredentials credentials = factory.credentialsFor(accessKeyAccount(), "eu-west-2").resolveCredentials();

        assertEquals("AKIAIOSFODNN7EXAMPLE", credentials.accessKeyId());
        assertEquals("wJalrXUtnFEMI/K7MDENG", credentials.secretAccessKey());
    }

    @Test
    void shouldRejectTokenAuthForAws() {
        Account account = Account.builder()
                .id("acc-2")
                .authType(AccountAuthType.TOKEN)
                .token("a-bearer-token")
                .build();

        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> factory.credentialsFor(account, "eu-west-2"));
        assertTrue(e.getMessage().contains("TOKEN auth cannot be used against real AWS"));
    }

    @Test
    void shouldRejectAccessKeyAuthWithNoSecret() {
        Account account = accessKeyAccount().toBuilder().secretAccessKey(null).build();

        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> factory.credentialsFor(account, "eu-west-2"));
        assertTrue(e.getMessage().contains("no secret access key"));
    }

    @Test
    void shouldPreferTheRequestedRegionThenTheAccountThenTheDefault() {
        Account account = accessKeyAccount();

        assertEquals("us-east-1", factory.resolveRegion(account, "us-east-1"));
        assertEquals("eu-west-2", factory.resolveRegion(account, null), "falls back to the account region");
        // The scan executor passes the literal "default" when a scan names no regions.
        assertEquals("eu-west-2", factory.resolveRegion(account, "default"));

        Account regionless = account.toBuilder().region(null).build();
        assertEquals("us-east-1", factory.resolveRegion(regionless, null), "falls back to configured default");
    }

    /**
     * The localhost story: no keys are stored against the account, so the AssumeRole
     * call is made with whatever the SDK's default chain finds - AWS_PROFILE, exported
     * environment credentials, an SSO session, or ~/.aws/credentials.
     */
    @Test
    void shouldAssumeRoleWithTheDefaultCredentialChainWhenNoKeysAreStored() {
        AwsCredentialsProvider base = factory.baseCredentialsFor(iamRoleAccount());

        assertInstanceOf(DefaultCredentialsProvider.class, base);
    }

    @Test
    void shouldAssumeRoleWithStoredKeysWhenTheyAreConfiguredAsBootstrapCredentials() {
        Account account = iamRoleAccount().toBuilder()
                .accessKeyId("AKIAIOSFODNN7EXAMPLE")
                .secretAccessKey("wJalrXUtnFEMI/K7MDENG")
                .build();

        AwsCredentials resolved = factory.baseCredentialsFor(account).resolveCredentials();

        assertEquals("AKIAIOSFODNN7EXAMPLE", resolved.accessKeyId());
        assertEquals("wJalrXUtnFEMI/K7MDENG", resolved.secretAccessKey());
    }

    @Test
    void shouldFallBackToTheDefaultChainWhenOnlyHalfAKeyPairIsStored() {
        Account account = iamRoleAccount().toBuilder().accessKeyId("AKIAIOSFODNN7EXAMPLE").build();

        assertInstanceOf(DefaultCredentialsProvider.class, factory.baseCredentialsFor(account));
    }

    @Test
    void shouldSendTheExternalIdOnlyWhenTheAccountHasOne() {
        Account account = iamRoleAccount();

        AssumeRoleRequest withoutExternalId = factory.assumeRoleRequestFor(account);
        assertNull(withoutExternalId.externalId());
        assertEquals("arn:aws:iam::123456789012:role/CertificateDiscoveryRole", withoutExternalId.roleArn());
        assertEquals("certplatform-acc-role", withoutExternalId.roleSessionName());

        Account withExternalId = account.toBuilder().externalId("shared-secret-external-id").build();
        assertEquals("shared-secret-external-id", factory.assumeRoleRequestFor(withExternalId).externalId());
    }

    @Test
    void shouldRejectIamRoleWithNoRoleArn() {
        Account account = iamRoleAccount().toBuilder().roleArn(null).build();

        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> factory.assumeRoleRequestFor(account));
        assertTrue(e.getMessage().contains("no role ARN"));
    }

    @Test
    void shouldKeepRoleSessionNameWithinTheAwsLimit() {
        Account account = iamRoleAccount().withId("acc-" + "0123456789".repeat(6));

        String sessionName = factory.assumeRoleRequestFor(account).roleSessionName();

        assertTrue(sessionName.length() <= 64, "AWS rejects role session names longer than 64 characters");
        assertTrue(sessionName.startsWith("certplatform-"));
    }
}
