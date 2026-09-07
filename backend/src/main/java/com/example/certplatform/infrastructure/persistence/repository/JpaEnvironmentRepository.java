package com.example.certplatform.infrastructure.persistence.repository;

import com.example.certplatform.infrastructure.persistence.entity.EnvironmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JpaEnvironmentRepository extends JpaRepository<EnvironmentEntity, String> {
    List<EnvironmentEntity> findByProviderId(String providerId);
}
