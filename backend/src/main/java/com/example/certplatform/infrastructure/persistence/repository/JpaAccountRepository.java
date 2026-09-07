package com.example.certplatform.infrastructure.persistence.repository;

import com.example.certplatform.infrastructure.persistence.entity.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JpaAccountRepository extends JpaRepository<AccountEntity, String> {
    List<AccountEntity> findByEnvironmentId(String environmentId);
}
