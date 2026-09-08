package com.example.certplatform.infrastructure.persistence.repository;

import com.example.certplatform.application.port.AccountRepositoryPort;
import com.example.certplatform.domain.model.Account;
import com.example.certplatform.infrastructure.persistence.entity.AccountEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class AccountRepositoryAdapter implements AccountRepositoryPort {

    private final JpaAccountRepository jpaRepository;

    public AccountRepositoryAdapter(JpaAccountRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
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
        entity.setToken(domain.getToken());
        entity.setRoleArn(domain.getRoleArn());
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
        account.setToken(entity.getToken());
        account.setRoleArn(entity.getRoleArn());
        account.setStatus(entity.getStatus());
        account.setCreatedAt(entity.getCreatedAt());
        account.setUpdatedAt(entity.getUpdatedAt());
        return account;
    }
}
