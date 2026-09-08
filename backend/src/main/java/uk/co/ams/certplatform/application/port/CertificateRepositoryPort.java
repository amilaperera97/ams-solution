package com.example.certplatform.application.port;

import com.example.certplatform.domain.model.Certificate;
import java.util.List;

public interface CertificateRepositoryPort {
    Certificate save(Certificate certificate);
    List<Certificate> findByScanId(String scanId);
    java.util.Optional<Certificate> findById(String id);
    long count();
    long countByStatus(String status);
}
