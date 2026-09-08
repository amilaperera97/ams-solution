package uk.co.ams.certplatform.infrastructure.cloud.aws;

import uk.co.ams.certplatform.domain.enums.AccountAuthType;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentials;

import static org.junit.jupiter.api.Assertions.*;

class AwsClientFactoryTest {

    private final CloudProviderProperties properties = new CloudProviderProperties();
    private final AwsClientFactory factory = new AwsClientFactory(properties);

    private Account accessKeyAccount() {
        Account account = new Account();
        account.setId("acc-1");
        account.setAuthType(AccountAuthType.ACCESS_KEY);
        account.setAccessKeyId("AKIAIOSFODNN7EXAMPLE");
        account.setSecretAccessKey("wJalrXUtnFEMI/K7MDENG");
        account.setRegion("eu-west-2");
        return account;
    }

    @Test
    void shouldBuildStaticCredentialsFromStoredAccessKey() {
        AwsCredentials credentials = factory.credentialsFor(accessKeyAccount(), "eu-west-2").resolveCredentials();

        assertEquals("AKIAIOSFODNN7EXAMPLE", credentials.accessKeyId());
        assertEquals("wJalrXUtnFEMI/K7MDENG", credentials.secretAccessKey());
    }

    @Test
    void shouldRejectTokenAuthForAws() {
        Account account = new Account();
        account.setId("acc-2");
        account.setAuthType(AccountAuthType.TOKEN);
        account.setToken("a-bearer-token");

        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> factory.credentialsFor(account, "eu-west-2"));
        assertTrue(e.getMessage().contains("TOKEN auth cannot be used against real AWS"));
    }

    @Test
    void shouldRejectAccessKeyAuthWithNoSecret() {
        Account account = accessKeyAccount();
        account.setSecretAccessKey(null);

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

        account.setRegion(null);
        assertEquals("us-east-1", factory.resolveRegion(account, null), "falls back to configured default");
    }
}
