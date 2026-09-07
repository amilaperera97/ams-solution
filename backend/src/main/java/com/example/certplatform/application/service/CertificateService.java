package com.example.certplatform.application.service;

import com.example.certplatform.application.port.CertificateRepositoryPort;
import com.example.certplatform.domain.model.Certificate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CertificateService {

    private final CertificateRepositoryPort certificateRepositoryPort;

    public CertificateService(CertificateRepositoryPort certificateRepositoryPort) {
        this.certificateRepositoryPort = certificateRepositoryPort;
    }

    public List<Certificate> getCertificatesByScanId(String scanId) {
        return certificateRepositoryPort.findByScanId(scanId);
    }

    public Optional<Certificate> getCertificateById(String id) {
        return certificateRepositoryPort.findById(id);
    }
}
