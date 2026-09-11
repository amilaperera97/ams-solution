package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.application.port.AccountRepositoryPort;
import uk.co.ams.certplatform.application.port.EnvironmentRepositoryPort;
import uk.co.ams.certplatform.application.port.ProviderRepositoryPort;
import uk.co.ams.certplatform.domain.enums.AccountAuthType;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.domain.model.Environment;
import uk.co.ams.certplatform.domain.model.Provider;
import uk.co.ams.certplatform.domain.model.ConnectionTestResult;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import uk.co.ams.certplatform.shared.security.SecretCipher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AccountService {

    /** AKIA... for long-lived users, ASIA... for temporary session keys. */
    private static final Pattern AWS_ACCESS_KEY_ID = Pattern.compile("^(AKIA|ASIA)[A-Z0-9]{16}$");
    private static final Pattern AWS_ROLE_ARN = Pattern.compile("^arn:aws[a-z-]*:iam::\\d{12}:role/.+$");
    private static final Pattern AWS_REGION = Pattern.compile("^[a-z]{2}(-[a-z]+){1,2}-\\d$");

    private final AccountRepositoryPort accountRepositoryPort;
    private final EnvironmentRepositoryPort environmentRepositoryPort;
    private final ProviderRepositoryPort providerRepositoryPort;
    private final CloudProviderResolver cloudProviderResolver;
    private final CloudProviderProperties cloudProviderProperties;
    private final SecretCipher secretCipher;

    public AccountService(AccountRepositoryPort accountRepositoryPort,
                          EnvironmentRepositoryPort environmentRepositoryPort,
                          ProviderRepositoryPort providerRepositoryPort,
                          CloudProviderResolver cloudProviderResolver,
                          CloudProviderProperties cloudProviderProperties,
                          SecretCipher secretCipher) {
        this.accountRepositoryPort = accountRepositoryPort;
        this.environmentRepositoryPort = environmentRepositoryPort;
        this.providerRepositoryPort = providerRepositoryPort;
        this.cloudProviderResolver = cloudProviderResolver;
        this.cloudProviderProperties = cloudProviderProperties;
        this.secretCipher = secretCipher;
    }

    public Account createAccount(String environmentId, String name, String accountId,
                                 AccountAuthType authType, AccountCredentials credentials) {
        Environment env = environmentRepositoryPort.findById(environmentId)
            .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + environmentId));

        Provider provider = providerRepositoryPort.findById(env.providerId())
            .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + env.providerId()));

        AccountCredentials creds = (credentials != null ? credentials : AccountCredentials.none()).trimmed();

        validate(accountId, authType, creds, provider.type());

        Instant now = Instant.now();
        Account account = withCredentials(Account.builder(), authType, creds)
                .id("acc-" + UUID.randomUUID())
                .organisationId(env.organisationId())
                .providerId(env.providerId())
                .environmentId(environmentId)
                .name(name)
                .accountId(accountId)
                .status("ACTIVE")
                .createdAt(now)
                .updatedAt(now)
                .build();

        return accountRepositoryPort.save(account);
    }

    /**
     * Applies a partial change. Anything the caller left null or blank keeps the value
     * already stored, so a client that has never been shown a secret (they are not in
     * any API response) can still edit the account without wiping it.
     */
    public Account updateAccount(String id, String name, String accountId,
                                 AccountAuthType authType, AccountCredentials credentials) {
        Account existing = accountRepositoryPort.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));

        Provider provider = providerRepositoryPort.findById(existing.providerId())
            .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + existing.providerId()));

        AccountCredentials merged = (credentials != null ? credentials : AccountCredentials.none())
                .trimmed()
                .mergedOver(AccountCredentials.of(existing));
        AccountAuthType effectiveAuthType = authType != null ? authType : existing.authType();
        String effectiveAccountId = isPresent(accountId) ? accountId.trim() : existing.accountId();

        validate(effectiveAccountId, effectiveAuthType, merged, provider.type());

        Account updated = withCredentials(existing.toBuilder(), effectiveAuthType, merged)
                .name(isPresent(name) ? name.trim() : existing.name())
                .accountId(effectiveAccountId)
                .updatedAt(Instant.now())
                .build();

        return accountRepositoryPort.save(updated);
    }

    /** Secrets are encrypted on the way into the database by AccountRepositoryAdapter. */
    private Account.Builder withCredentials(Account.Builder builder, AccountAuthType authType,
                                            AccountCredentials creds) {
        return builder
                .authType(authType)
                .token(creds.token())
                .roleArn(creds.roleArn())
                .externalId(creds.externalId())
                .accessKeyId(creds.accessKeyId())
                .secretAccessKey(creds.secretAccessKey())
                .region(creds.region());
    }

    private void validate(String accountId, AccountAuthType authType, AccountCredentials creds,
                          CloudProviderType providerType) {
        boolean aws = providerType == CloudProviderType.AWS;
        boolean realMode = cloudProviderProperties.isReal(providerType);

        if (aws) {
            if (accountId == null || !accountId.matches("\\d{12}")) {
                throw new IllegalArgumentException("AWS account ID must contain exactly 12 digits.");
            }
        } else if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account ID is required.");
        }

        if (authType == null) {
            throw new IllegalArgumentException("Auth type is required (TOKEN, IAM_ROLE or ACCESS_KEY).");
        }

        // Refuse to write a live cloud secret to disk unencrypted, whatever the profile says.
        if (realMode && !secretCipher.encryptionEnabled()
                && (creds.hasAccessKey() || isPresent(creds.token()))) {
            throw new IllegalStateException(
                "Refusing to store credentials for a REAL provider without encryption. "
                + "Set CERTPLATFORM_SECRET_KEY and restart.");
        }

        if (isPresent(creds.region()) && aws && !AWS_REGION.matcher(creds.region().trim()).matches()) {
            throw new IllegalArgumentException("Region must look like an AWS region, e.g. eu-west-2.");
        }

        switch (authType) {
            case TOKEN -> {
                if (!isPresent(creds.token())) {
                    throw new IllegalArgumentException("Token is required when authType is TOKEN.");
                }
                if (aws && realMode) {
                    throw new IllegalArgumentException(
                        "TOKEN auth cannot reach real AWS. Use ACCESS_KEY or IAM_ROLE.");
                }
            }
            case IAM_ROLE -> {
                if (!isPresent(creds.roleArn())) {
                    throw new IllegalArgumentException("Role ARN is required when authType is IAM_ROLE.");
                }
                if (aws && realMode) {
                    if (!AWS_ROLE_ARN.matcher(creds.roleArn().trim()).matches()) {
                        throw new IllegalArgumentException(
                            "Role ARN must look like arn:aws:iam::123456789012:role/RoleName.");
                    }
                    requireRegion(creds);
                }
            }
            case ACCESS_KEY -> {
                if (!isPresent(creds.accessKeyId()) || !isPresent(creds.secretAccessKey())) {
                    throw new IllegalArgumentException(
                        "Access key ID and secret access key are both required when authType is ACCESS_KEY.");
                }
                if (aws && realMode) {
                    if (!AWS_ACCESS_KEY_ID.matcher(creds.accessKeyId().trim()).matches()) {
                        throw new IllegalArgumentException(
                            "Access key ID must be 20 characters starting with AKIA or ASIA.");
                    }
                    requireRegion(creds);
                }
            }
        }
    }

    private void requireRegion(AccountCredentials creds) {
        if (!isPresent(creds.region())) {
            throw new IllegalArgumentException("Region is required when the provider is in REAL mode.");
        }
    }

    public Optional<Account> getAccount(String id) {
        return accountRepositoryPort.findById(id);
    }

    public List<Account> getAccountsByEnvironmentId(String environmentId) {
        return accountRepositoryPort.findByEnvironmentId(environmentId);
    }

    public ConnectionTestResult testConnection(String accountId) {
        Account account = accountRepositoryPort.findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));

        Provider provider = providerRepositoryPort.findById(account.providerId())
            .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + account.providerId()));

        return cloudProviderResolver.getAdapter(provider.type()).testConnection(account);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
