package uk.co.ams.certplatform.infrastructure.persistence.repository;

import uk.co.ams.certplatform.application.port.AccountRepositoryPort;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.infrastructure.persistence.entity.AccountEntity;
import uk.co.ams.certplatform.shared.security.SecretCipher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class AccountRepositoryAdapter implements AccountRepositoryPort {

    private final JpaAccountRepository jpaRepository;
    private final SecretCipher secretCipher;

    public AccountRepositoryAdapter(JpaAccountRepository jpaRepository, SecretCipher secretCipher) {
        this.jpaRepository = jpaRepository;
        this.secretCipher = secretCipher;
    }

    @Override
    public Account save(Account account) {
        AccountEntity entity = toEntity(account);
        AccountEntity savedEntity = jpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public Optional<Account> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Account> findByEnvironmentId(String environmentId) {
        return jpaRepository.findByEnvironmentId(environmentId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    private AccountEntity toEntity(Account domain) {
        if (domain == null) return null;
        AccountEntity entity = new AccountEntity();
        entity.setId(domain.getId());
        entity.setOrganisationId(domain.getOrganisationId());
        entity.setProviderId(domain.getProviderId());
        entity.setEnvironmentId(domain.getEnvironmentId());
        entity.setName(domain.getName());
        entity.setAccountId(domain.getAccountId());
        entity.setAuthType(domain.getAuthType());
        entity.setToken(secretCipher.encrypt(domain.getToken()));
        entity.setRoleArn(domain.getRoleArn());
        entity.setExternalId(domain.getExternalId());
        entity.setAccessKeyId(domain.getAccessKeyId());
        entity.setSecretAccessKey(secretCipher.encrypt(domain.getSecretAccessKey()));
        entity.setRegion(domain.getRegion());
        entity.setStatus(domain.getStatus());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    private Account toDomain(AccountEntity entity) {
        if (entity == null) return null;
        Account account = new Account();
        account.setId(entity.getId());
        account.setOrganisationId(entity.getOrganisationId());
        account.setProviderId(entity.getProviderId());
        account.setEnvironmentId(entity.getEnvironmentId());
        account.setName(entity.getName());
        account.setAccountId(entity.getAccountId());
        account.setAuthType(entity.getAuthType());
        account.setToken(secretCipher.decrypt(entity.getToken()));
        account.setRoleArn(entity.getRoleArn());
        account.setExternalId(entity.getExternalId());
        account.setAccessKeyId(entity.getAccessKeyId());
        account.setSecretAccessKey(secretCipher.decrypt(entity.getSecretAccessKey()));
        account.setRegion(entity.getRegion());
        account.setStatus(entity.getStatus());
        account.setCreatedAt(entity.getCreatedAt());
        account.setUpdatedAt(entity.getUpdatedAt());
        return account;
    }
}
