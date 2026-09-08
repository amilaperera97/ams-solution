package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.application.port.AccountRepositoryPort;
import uk.co.ams.certplatform.application.port.EnvironmentRepositoryPort;
import uk.co.ams.certplatform.application.port.ProviderRepositoryPort;
import uk.co.ams.certplatform.domain.enums.AccountAuthType;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.domain.model.Environment;
import uk.co.ams.certplatform.domain.model.Provider;
import uk.co.ams.certplatform.domain.model.ConnectionTestResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepositoryPort accountRepositoryPort;
    private final EnvironmentRepositoryPort environmentRepositoryPort;
    private final ProviderRepositoryPort providerRepositoryPort;
    private final CloudProviderResolver cloudProviderResolver;

    public AccountService(AccountRepositoryPort accountRepositoryPort, 
                          EnvironmentRepositoryPort environmentRepositoryPort,
                          ProviderRepositoryPort providerRepositoryPort,
                          CloudProviderResolver cloudProviderResolver) {
        this.accountRepositoryPort = accountRepositoryPort;
        this.environmentRepositoryPort = environmentRepositoryPort;
        this.providerRepositoryPort = providerRepositoryPort;
        this.cloudProviderResolver = cloudProviderResolver;
    }

    public Account createAccount(String environmentId, String name, String accountId, AccountAuthType authType, String token, String roleArn) {
        Environment env = environmentRepositoryPort.findById(environmentId)
            .orElseThrow(() -> new IllegalArgumentException("Environment not found: " + environmentId));

        // Validation rules
        if (accountId == null || !accountId.matches("\\d{12}")) {
            throw new IllegalArgumentException("AWS account ID must contain exactly 12 digits.");
        }

        if (authType == AccountAuthType.TOKEN && (token == null || token.isBlank())) {
            throw new IllegalArgumentException("Token is required when authType is TOKEN.");
        }

        if (authType == AccountAuthType.IAM_ROLE && (roleArn == null || roleArn.isBlank())) {
            throw new IllegalArgumentException("Role ARN is required when authType is IAM_ROLE.");
        }

        Account account = new Account();
        account.setId("acc-" + UUID.randomUUID().toString());
        account.setOrganisationId(env.getOrganisationId());
        account.setProviderId(env.getProviderId());
        account.setEnvironmentId(environmentId);
        account.setName(name);
        account.setAccountId(accountId);
        account.setAuthType(authType);
        
        // In a real application, token/role credentials would be encrypted or passed to a secret manager here.
        // For development we store them, but do not expose them.
        account.setToken(token);
        account.setRoleArn(roleArn);
        
        account.setStatus("ACTIVE");
        account.setCreatedAt(Instant.now());
        account.setUpdatedAt(Instant.now());

        return accountRepositoryPort.save(account);
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
            
        Provider provider = providerRepositoryPort.findById(account.getProviderId())
            .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + account.getProviderId()));
            
        return cloudProviderResolver.getAdapter(provider.getType()).testConnection(account);
    }
}
