package uk.co.ams.certplatform.infrastructure.persistence.repository;

import uk.co.ams.certplatform.application.port.CertificateRepositoryPort;
import uk.co.ams.certplatform.domain.model.Certificate;
import uk.co.ams.certplatform.infrastructure.persistence.entity.CertificateEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.UUID;
import java.time.Instant;

@Component
public class CertificateRepositoryAdapter implements CertificateRepositoryPort {

    private final JpaCertificateRepository jpaRepository;

    public CertificateRepositoryAdapter(JpaCertificateRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Certificate save(Certificate certificate) {
        if (certificate.getId() == null) {
            certificate.setId("cert-" + UUID.randomUUID().toString());
        }
        if (certificate.getCreatedAt() == null) {
            certificate.setCreatedAt(Instant.now());
        }
        CertificateEntity entity = toEntity(certificate);
        CertificateEntity savedEntity = jpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public List<Certificate> findByScanId(String scanId) {
        return jpaRepository.findByScanId(scanId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public java.util.Optional<Certificate> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    @Override
    public long countByStatus(String status) {
        return jpaRepository.countByStatus(status);
    }

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private CertificateEntity toEntity(Certificate domain) {
        if (domain == null) return null;
        CertificateEntity entity = new CertificateEntity();
        entity.setId(domain.getId());
        entity.setScanId(domain.getScanId());
        entity.setProvider(domain.getProvider());
        entity.setAccountId(domain.getAccountId());
        entity.setEnvironment(domain.getEnvironment());
        entity.setRegion(domain.getRegion());
        entity.setDomain(domain.getDomain());
        entity.setStatus(domain.getStatus());
        entity.setService(domain.getService());
        entity.setResource(domain.getResource());
        entity.setIssuedDate(domain.getIssuedDate());
        entity.setExpiryDate(domain.getExpiryDate());
        entity.setIssuer(domain.getIssuer());
        entity.setAlgorithm(domain.getAlgorithm());
        entity.setAutoRenewal(domain.getAutoRenewal());
        entity.setCreatedAt(domain.getCreatedAt());
        
        entity.setFingerprint(domain.getFingerprint());
        entity.setSerialNumber(domain.getSerialNumber());
        entity.setSubject(domain.getSubject());
        entity.setSourceType(domain.getSourceType());
        entity.setKeySize(domain.getKeySize());

        try {
            if (!domain.getUsages().isEmpty()) {
                entity.setUsagesJson(objectMapper.writeValueAsString(domain.getUsages()));
            }
            if (!domain.getTags().isEmpty()) {
                entity.setTagsJson(objectMapper.writeValueAsString(domain.getTags()));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize certificate metadata", e);
        }

        return entity;
    }

    private Certificate toDomain(CertificateEntity entity) {
        if (entity == null) return null;
        Certificate cert = new Certificate();
        cert.setId(entity.getId());
        cert.setScanId(entity.getScanId());
        cert.setProvider(entity.getProvider());
        cert.setAccountId(entity.getAccountId());
        cert.setEnvironment(entity.getEnvironment());
        cert.setRegion(entity.getRegion());
        cert.setDomain(entity.getDomain());
        cert.setStatus(entity.getStatus());
        cert.setService(entity.getService());
        cert.setResource(entity.getResource());
        cert.setIssuedDate(entity.getIssuedDate());
        cert.setExpiryDate(entity.getExpiryDate());
        cert.setIssuer(entity.getIssuer());
        cert.setAlgorithm(entity.getAlgorithm());
        cert.setAutoRenewal(entity.getAutoRenewal());
        cert.setCreatedAt(entity.getCreatedAt());

        cert.setFingerprint(entity.getFingerprint());
        cert.setSerialNumber(entity.getSerialNumber());
        cert.setSubject(entity.getSubject());
        cert.setSourceType(entity.getSourceType());
        cert.setKeySize(entity.getKeySize());

        try {
            if (entity.getUsagesJson() != null) {
                java.util.List<uk.co.ams.certplatform.domain.model.CertificateUsage> usages = 
                        objectMapper.readValue(entity.getUsagesJson(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                usages.forEach(cert::addUsage);
            }
            if (entity.getTagsJson() != null) {
                java.util.List<uk.co.ams.certplatform.domain.model.ResourceTag> tags = 
                        objectMapper.readValue(entity.getTagsJson(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
                tags.forEach(cert::addTag);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize certificate metadata", e);
        }

        return cert;
    }
}
