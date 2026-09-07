package com.example.certplatform.infrastructure.persistence.repository;

import com.example.certplatform.infrastructure.persistence.entity.CertificateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JpaCertificateRepository extends JpaRepository<CertificateEntity, String> {
    List<CertificateEntity> findByScanId(String scanId);
    long countByStatus(String status);
}
