package uk.co.ams.certplatform.infrastructure.persistence.repository;

import uk.co.ams.certplatform.domain.enums.AccountAuthType;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.infrastructure.persistence.entity.AccountEntity;
import uk.co.ams.certplatform.shared.security.AesGcmSecretCipher;
import uk.co.ams.certplatform.shared.security.SecretCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Everything that reaches the database goes through this adapter, so this is where
 * "secrets are encrypted at rest" is actually enforced.
 */
class AccountRepositoryAdapterTest {

    private static final String KEY = "dGVzdC1rZXktdGVzdC1rZXktdGVzdC1rZXktMTI=";

    @Mock
    private JpaAccountRepository jpaRepository;

    private final SecretCipher cipher = new AesGcmSecretCipher(KEY);
    private AccountRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adapter = new AccountRepositoryAdapter(jpaRepository, cipher);
    }

    private Account accessKeyAccount() {
        return Account.builder()
                .id("acc-1")
                .authType(AccountAuthType.ACCESS_KEY)
                .accountId("123456789012")
                .accessKeyId("AKIAIOSFODNN7EXAMPLE")
                .secretAccessKey("wJalrXUtnFEMI/K7MDENG")
                .region("eu-west-2")
                .build();
    }

    @Test
    void shouldEncryptSecretsBeforeTheyReachTheDatabase() {
        when(jpaRepository.save(any(AccountEntity.class))).thenAnswer(i -> i.getArgument(0));

        adapter.save(accessKeyAccount());

        ArgumentCaptor<AccountEntity> captor = ArgumentCaptor.forClass(AccountEntity.class);
        verify(jpaRepository).save(captor.capture());
        AccountEntity stored = captor.getValue();

        assertTrue(SecretCipher.isEncrypted(stored.getSecretAccessKey()),
                "the secret access key must be stored as ciphertext");
        assertFalse(stored.getSecretAccessKey().contains("wJalrXUtnFEMI"));
        // The access key id is an identifier, not a secret - it stays readable so the
        // response can mask it and an operator can tell which key is configured.
        assertEquals("AKIAIOSFODNN7EXAMPLE", stored.getAccessKeyId());
        assertEquals("eu-west-2", stored.getRegion());
    }

    @Test
    void shouldEncryptTokensToo() {
        when(jpaRepository.save(any(AccountEntity.class))).thenAnswer(i -> i.getArgument(0));
        Account account = accessKeyAccount().toBuilder()
                .authType(AccountAuthType.TOKEN)
                .secretAccessKey(null)
                .token("a-bearer-token")
                .build();

        adapter.save(account);

        ArgumentCaptor<AccountEntity> captor = ArgumentCaptor.forClass(AccountEntity.class);
        verify(jpaRepository).save(captor.capture());
        assertTrue(SecretCipher.isEncrypted(captor.getValue().getToken()));
    }

    @Test
    void shouldDecryptSecretsOnTheWayBackOut() {
        when(jpaRepository.save(any(AccountEntity.class))).thenAnswer(i -> i.getArgument(0));
        Account saved = adapter.save(accessKeyAccount());

        assertEquals("wJalrXUtnFEMI/K7MDENG", saved.secretAccessKey(),
                "callers get plaintext back so the SDK can sign with it");

        ArgumentCaptor<AccountEntity> captor = ArgumentCaptor.forClass(AccountEntity.class);
        verify(jpaRepository).save(captor.capture());
        when(jpaRepository.findById("acc-1")).thenReturn(Optional.of(captor.getValue()));

        assertEquals("wJalrXUtnFEMI/K7MDENG", adapter.findById("acc-1").orElseThrow().secretAccessKey());
    }

    @Test
    void shouldStillReadRowsWrittenBeforeEncryptionExisted() {
        AccountEntity legacy = new AccountEntity();
        legacy.setId("acc-legacy");
        legacy.setAuthType(AccountAuthType.ACCESS_KEY);
        legacy.setAccessKeyId("AKIAIOSFODNN7EXAMPLE");
        legacy.setSecretAccessKey("plaintext-from-an-older-build");
        when(jpaRepository.findById("acc-legacy")).thenReturn(Optional.of(legacy));

        assertEquals("plaintext-from-an-older-build",
                adapter.findById("acc-legacy").orElseThrow().secretAccessKey());
    }
}
