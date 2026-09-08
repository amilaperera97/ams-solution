package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.application.port.AccountRepositoryPort;
import uk.co.ams.certplatform.application.port.EnvironmentRepositoryPort;
import uk.co.ams.certplatform.application.port.ProviderRepositoryPort;
import uk.co.ams.certplatform.domain.enums.AccountAuthType;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.enums.ProviderMode;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.domain.model.Environment;
import uk.co.ams.certplatform.domain.model.Provider;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import uk.co.ams.certplatform.shared.security.AesGcmSecretCipher;
import uk.co.ams.certplatform.shared.security.PlaintextSecretCipher;
import uk.co.ams.certplatform.shared.security.SecretCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountServiceTest {

    private static final String ENV_ID = "env-123";
    private static final String PROVIDER_ID = "prov-123";

    @Mock
    private AccountRepositoryPort accountRepositoryPort;

    @Mock
    private EnvironmentRepositoryPort environmentRepositoryPort;

    @Mock
    private ProviderRepositoryPort providerRepositoryPort;

    @Mock
    private CloudProviderResolver cloudProviderResolver;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        Environment env = new Environment(ENV_ID, "org-123", PROVIDER_ID, "PROD", "Desc", "ACTIVE", null, null);
        when(environmentRepositoryPort.findById(ENV_ID)).thenReturn(Optional.of(env));

        Provider provider = new Provider(PROVIDER_ID, "AWS Provider", "org-123", CloudProviderType.AWS, "ACTIVE", null, null);
        when(providerRepositoryPort.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
    }

    /** Service wired for the dev profile: AWS in MOCK, no encryption key. */
    private AccountService mockModeService() {
        return service(ProviderMode.MOCK, new PlaintextSecretCipher());
    }

    /** Service wired for the qa profile: AWS in REAL, encryption on. */
    private AccountService realModeService() {
        return service(ProviderMode.REAL, new AesGcmSecretCipher("dGVzdC1rZXktdGVzdC1rZXktdGVzdC1rZXktMTI="));
    }

    private AccountService service(ProviderMode mode, SecretCipher cipher) {
        CloudProviderProperties.ProviderSettings aws = new CloudProviderProperties.ProviderSettings();
        aws.setMode(mode);
        CloudProviderProperties properties = new CloudProviderProperties();
        properties.setProviders(Map.of("aws", aws));
        return new AccountService(accountRepositoryPort, environmentRepositoryPort, providerRepositoryPort,
                cloudProviderResolver, properties, cipher);
    }

    private void stubSave() {
        Account saved = new Account();
        saved.setId("acc-123");
        when(accountRepositoryPort.save(any(Account.class))).thenReturn(saved);
    }

    @Test
    void shouldCreateAccountWithIamRole() {
        stubSave();

        Account result = mockModeService().createAccount(ENV_ID, "Test Account", "123456789012",
                AccountAuthType.IAM_ROLE,
                new AccountCredentials(null, "arn:aws:iam::123456789012:role/Role", null, null, null, null));

        assertNotNull(result);
        assertEquals("acc-123", result.getId());
        verify(accountRepositoryPort, times(1)).save(any(Account.class));
    }

    @Test
    void shouldThrowExceptionWhenAccountIdInvalidForAws() {
        AccountService service = mockModeService();
        AccountCredentials creds = new AccountCredentials(null, "arn", null, null, null, null);

        Exception exception = assertThrows(IllegalArgumentException.class,
            () -> service.createAccount(ENV_ID, "Test Account", "invalid-id", AccountAuthType.IAM_ROLE, creds));

        assertTrue(exception.getMessage().contains("12 digits"));
    }

    @Test
    void shouldThrowExceptionWhenRoleArnMissingForIamRole() {
        AccountService service = mockModeService();

        Exception exception = assertThrows(IllegalArgumentException.class,
            () -> service.createAccount(ENV_ID, "Test Account", "123456789012",
                    AccountAuthType.IAM_ROLE, AccountCredentials.none()));

        assertTrue(exception.getMessage().contains("Role ARN is required"));
    }

    @Test
    void shouldThrowExceptionWhenTokenMissingForTokenAuth() {
        AccountService service = mockModeService();

        Exception exception = assertThrows(IllegalArgumentException.class,
            () -> service.createAccount(ENV_ID, "Test Account", "123456789012",
                    AccountAuthType.TOKEN, AccountCredentials.none()));

        assertTrue(exception.getMessage().contains("Token is required"));
    }

    @Test
    void shouldStoreRealAccessKeyCredentials() {
        stubSave();

        realModeService().createAccount(ENV_ID, "QA Account", "123456789012", AccountAuthType.ACCESS_KEY,
                new AccountCredentials(null, null, null, "AKIAIOSFODNN7EXAMPLE", "wJalrXUtnFEMI/K7MDENG", "eu-west-2"));

        org.mockito.ArgumentCaptor<Account> captor = org.mockito.ArgumentCaptor.forClass(Account.class);
        verify(accountRepositoryPort).save(captor.capture());
        Account persisted = captor.getValue();

        assertEquals("AKIAIOSFODNN7EXAMPLE", persisted.getAccessKeyId());
        assertEquals("wJalrXUtnFEMI/K7MDENG", persisted.getSecretAccessKey());
        assertEquals("eu-west-2", persisted.getRegion());
        assertEquals(AccountAuthType.ACCESS_KEY, persisted.getAuthType());
    }

    @Test
    void shouldRejectAccessKeyWithoutSecret() {
        AccountService service = realModeService();
        AccountCredentials creds = new AccountCredentials(null, null, null, "AKIAIOSFODNN7EXAMPLE", null, "eu-west-2");

        Exception exception = assertThrows(IllegalArgumentException.class,
            () -> service.createAccount(ENV_ID, "QA Account", "123456789012", AccountAuthType.ACCESS_KEY, creds));

        assertTrue(exception.getMessage().contains("secret access key"));
    }

    @Test
    void shouldRejectMalformedAccessKeyIdInRealMode() {
        AccountService service = realModeService();
        AccountCredentials creds = new AccountCredentials(null, null, null, "not-a-key", "secret", "eu-west-2");

        Exception exception = assertThrows(IllegalArgumentException.class,
            () -> service.createAccount(ENV_ID, "QA Account", "123456789012", AccountAuthType.ACCESS_KEY, creds));

        assertTrue(exception.getMessage().contains("AKIA"));
    }

    @Test
    void shouldRequireRegionInRealMode() {
        AccountService service = realModeService();
        AccountCredentials creds = new AccountCredentials(null, null, null, "AKIAIOSFODNN7EXAMPLE", "secret", null);

        Exception exception = assertThrows(IllegalArgumentException.class,
            () -> service.createAccount(ENV_ID, "QA Account", "123456789012", AccountAuthType.ACCESS_KEY, creds));

        assertTrue(exception.getMessage().contains("Region is required"));
    }

    @Test
    void shouldRejectTokenAuthAgainstRealAws() {
        AccountService service = realModeService();
        AccountCredentials creds = new AccountCredentials("a-token", null, null, null, null, "eu-west-2");

        Exception exception = assertThrows(IllegalArgumentException.class,
            () -> service.createAccount(ENV_ID, "QA Account", "123456789012", AccountAuthType.TOKEN, creds));

        assertTrue(exception.getMessage().contains("cannot reach real AWS"));
    }

    @Test
    void shouldRefuseRealCredentialsWhenEncryptionIsUnavailable() {
        AccountService service = service(ProviderMode.REAL, new PlaintextSecretCipher());
        AccountCredentials creds = new AccountCredentials(null, null, null, "AKIAIOSFODNN7EXAMPLE", "secret", "eu-west-2");

        Exception exception = assertThrows(IllegalStateException.class,
            () -> service.createAccount(ENV_ID, "QA Account", "123456789012", AccountAuthType.ACCESS_KEY, creds));

        assertTrue(exception.getMessage().contains("CERTPLATFORM_SECRET_KEY"));
    }

    @Test
    void shouldRejectInvalidRegionFormat() {
        AccountService service = realModeService();
        AccountCredentials creds = new AccountCredentials(null, null, null, "AKIAIOSFODNN7EXAMPLE", "secret", "London");

        Exception exception = assertThrows(IllegalArgumentException.class,
            () -> service.createAccount(ENV_ID, "QA Account", "123456789012", AccountAuthType.ACCESS_KEY, creds));

        assertTrue(exception.getMessage().contains("AWS region"));
    }
}
