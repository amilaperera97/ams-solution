package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.application.port.AccountRepositoryPort;
import uk.co.ams.certplatform.application.port.EnvironmentRepositoryPort;
import uk.co.ams.certplatform.domain.enums.AccountAuthType;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.domain.model.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountServiceTest {

    @Mock
    private AccountRepositoryPort accountRepositoryPort;

    @Mock
    private EnvironmentRepositoryPort environmentRepositoryPort;

    @InjectMocks
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldCreateAccountWithIamRole() {
        String envId = "env-123";
        Environment env = new Environment(envId, "org-123", "prov-123", "PROD", "Desc", "ACTIVE", null, null);
        Account savedAccount = new Account();
        savedAccount.setId("acc-123");
        
        when(environmentRepositoryPort.findById(envId)).thenReturn(Optional.of(env));
        when(accountRepositoryPort.save(any(Account.class))).thenReturn(savedAccount);

        Account result = accountService.createAccount(envId, "Test Account", "123456789012", AccountAuthType.IAM_ROLE, null, "arn:aws:iam::123456789012:role/Role");

        assertNotNull(result);
        assertEquals("acc-123", result.getId());
        verify(accountRepositoryPort, times(1)).save(any(Account.class));
    }

    @Test
    void shouldThrowExceptionWhenAccountIdInvalidForAws() {
        String envId = "env-123";
        Environment env = new Environment(envId, "org-123", "prov-123", "PROD", "Desc", "ACTIVE", null, null);
        
        when(environmentRepositoryPort.findById(envId)).thenReturn(Optional.of(env));

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            accountService.createAccount(envId, "Test Account", "invalid-id", AccountAuthType.IAM_ROLE, null, "arn");
        });
        
        assertTrue(exception.getMessage().contains("12 digits"));
    }

    @Test
    void shouldThrowExceptionWhenRoleArnMissingForIamRole() {
        String envId = "env-123";
        Environment env = new Environment(envId, "org-123", "prov-123", "PROD", "Desc", "ACTIVE", null, null);
        
        when(environmentRepositoryPort.findById(envId)).thenReturn(Optional.of(env));

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            accountService.createAccount(envId, "Test Account", "123456789012", AccountAuthType.IAM_ROLE, null, null);
        });
        
        assertTrue(exception.getMessage().contains("Role ARN is required"));
    }

    @Test
    void shouldThrowExceptionWhenTokenMissingForTokenAuth() {
        String envId = "env-123";
        Environment env = new Environment(envId, "org-123", "prov-123", "PROD", "Desc", "ACTIVE", null, null);
        
        when(environmentRepositoryPort.findById(envId)).thenReturn(Optional.of(env));

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            accountService.createAccount(envId, "Test Account", "123456789012", AccountAuthType.TOKEN, null, null);
        });
        
        assertTrue(exception.getMessage().contains("Token is required"));
    }
}
