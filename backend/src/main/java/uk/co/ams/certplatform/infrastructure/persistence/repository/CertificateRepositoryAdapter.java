package uk.co.ams.certplatform.infrastructure.persistence.repository;

import uk.co.ams.certplatform.application.port.CertificateRepositoryPort;
import uk.co.ams.certplatform.domain.model.Certificate;
import uk.co.ams.certplatform.domain.model.CertificateUsage;
import uk.co.ams.certplatform.domain.model.ResourceTag;
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
        Certificate toSave = certificate.toBuilder()
                .id(certificate.id() != null ? certificate.id() : "cert-" + UUID.randomUUID())
                .createdAt(certificate.createdAt() != null ? certificate.createdAt() : Instant.now())
                .build();

        CertificateEntity entity = toEntity(toSave);
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
        entity.setId(domain.id());
        entity.setScanId(domain.scanId());
        entity.setProvider(domain.provider());
        entity.setAccountId(domain.accountId());
        entity.setRegion(domain.region());
        // domain, status and environment are NOT NULL in the schema, but a certificate
        // read off a disk may legitimately have no CN and no SAN. Substituting a marker
        // keeps the record - and the resource it was found on - rather than failing the
        // insert and taking every other certificate in the batch down with it.
        entity.setDomain(orUnknown(domain.domain()));
        entity.setStatus(orUnknown(domain.status()));
        entity.setEnvironment(orUnassigned(domain.environment()));
        entity.setService(domain.service());
        entity.setResource(domain.resource());
        entity.setIssuedDate(domain.issuedDate());
        entity.setExpiryDate(domain.expiryDate());
        entity.setIssuer(domain.issuer());
        entity.setAlgorithm(domain.algorithm());
        entity.setAutoRenewal(domain.autoRenewal());
        entity.setCreatedAt(domain.createdAt());
        
        entity.setFingerprint(domain.fingerprint());
        entity.setSerialNumber(domain.serialNumber());
        entity.setSubject(domain.subject());
        entity.setSourceType(domain.sourceType());
        entity.setKeySize(domain.keySize());

        try {
            if (!domain.usages().isEmpty()) {
                entity.setUsagesJson(objectMapper.writeValueAsString(domain.usages()));
            }
            if (!domain.tags().isEmpty()) {
                entity.setTagsJson(objectMapper.writeValueAsString(domain.tags()));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize certificate metadata", e);
        }

        return entity;
    }

    private static String orUnknown(String value) {
        return value != null && !value.isBlank() ? value : "unknown";
    }

    private static String orUnassigned(String value) {
        return value != null && !value.isBlank() ? value : "unassigned";
    }

    private Certificate toDomain(CertificateEntity entity) {
        if (entity == null) return null;
        Certificate.Builder cert = Certificate.builder()
                .id(entity.getId())
                .scanId(entity.getScanId())
                .provider(entity.getProvider())
                .accountId(entity.getAccountId())
                .environment(entity.getEnvironment())
                .region(entity.getRegion())
                .domain(entity.getDomain())
                .status(entity.getStatus())
                .service(entity.getService())
                .resource(entity.getResource())
                .issuedDate(entity.getIssuedDate())
                .expiryDate(entity.getExpiryDate())
                .issuer(entity.getIssuer())
                .algorithm(entity.getAlgorithm())
                .autoRenewal(entity.getAutoRenewal())
                .createdAt(entity.getCreatedAt())
                .fingerprint(entity.getFingerprint())
                .serialNumber(entity.getSerialNumber())
                .subject(entity.getSubject())
                .sourceType(entity.getSourceType())
                .keySize(entity.getKeySize());

        try {
            if (entity.getUsagesJson() != null) {
                cert.usages(objectMapper.readValue(entity.getUsagesJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<CertificateUsage>>() {}));
            }
            if (entity.getTagsJson() != null) {
                cert.tags(objectMapper.readValue(entity.getTagsJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<ResourceTag>>() {}));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize certificate metadata", e);
        }

        return cert.build();
    }
}
