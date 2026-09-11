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
        entity.setId(domain.id());
        entity.setOrganisationId(domain.organisationId());
        entity.setProviderId(domain.providerId());
        entity.setEnvironmentId(domain.environmentId());
        entity.setName(domain.name());
        entity.setAccountId(domain.accountId());
        entity.setAuthType(domain.authType());
        entity.setToken(secretCipher.encrypt(domain.token()));
        entity.setRoleArn(domain.roleArn());
        entity.setExternalId(domain.externalId());
        entity.setAccessKeyId(domain.accessKeyId());
        entity.setSecretAccessKey(secretCipher.encrypt(domain.secretAccessKey()));
        entity.setRegion(domain.region());
        entity.setStatus(domain.status());
        entity.setCreatedAt(domain.createdAt());
        entity.setUpdatedAt(domain.updatedAt());
        return entity;
    }

    private Account toDomain(AccountEntity entity) {
        if (entity == null) return null;
        return Account.builder()
                .id(entity.getId())
                .organisationId(entity.getOrganisationId())
                .providerId(entity.getProviderId())
                .environmentId(entity.getEnvironmentId())
                .name(entity.getName())
                .accountId(entity.getAccountId())
                .authType(entity.getAuthType())
                .token(secretCipher.decrypt(entity.getToken()))
                .roleArn(entity.getRoleArn())
                .externalId(entity.getExternalId())
                .accessKeyId(entity.getAccessKeyId())
                .secretAccessKey(secretCipher.decrypt(entity.getSecretAccessKey()))
                .region(entity.getRegion())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
