package com.example.certplatform.infrastructure.persistence.repository;

import com.example.certplatform.infrastructure.persistence.entity.ScanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaScanRepository extends JpaRepository<ScanEntity, String> {
}
