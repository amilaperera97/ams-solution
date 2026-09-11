package uk.co.ams.certplatform.infrastructure.persistence.repository;

import uk.co.ams.certplatform.application.port.ScanRepositoryPort;
import uk.co.ams.certplatform.domain.model.Scan;
import uk.co.ams.certplatform.infrastructure.persistence.entity.ScanEntity;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ScanRepositoryAdapter implements ScanRepositoryPort {

    private final JpaScanRepository jpaRepository;

    public ScanRepositoryAdapter(JpaScanRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Scan save(Scan scan) {
        ScanEntity entity = toEntity(scan);
        ScanEntity savedEntity = jpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public Optional<Scan> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Scan> findAll() {
        return jpaRepository.findAll().stream().map(this::toDomain).collect(Collectors.toList());
    }

    private ScanEntity toEntity(Scan domain) {
        if (domain == null) return null;
        ScanEntity entity = new ScanEntity();
        entity.setId(domain.id());
        entity.setName(domain.name());
        entity.setScopeType(domain.scopeType());
        entity.setProviderIds(toListString(domain.providerIds()));
        entity.setEnvironmentIds(toListString(domain.environmentIds()));
        entity.setAccountIds(toListString(domain.accountIds()));
        entity.setRegions(toListString(domain.regions()));
        entity.setServices(toListString(domain.services()));
        entity.setStatus(domain.status());
        entity.setProgressPercent(domain.progressPercent());
        entity.setAccountsTotal(domain.accountsTotal());
        entity.setAccountsCompleted(domain.accountsCompleted());
        entity.setCertificatesDiscovered(domain.certificatesDiscovered());
        entity.setCreatedAt(domain.createdAt());
        entity.setUpdatedAt(domain.updatedAt());
        return entity;
    }

    private Scan toDomain(ScanEntity entity) {
        if (entity == null) return null;
        return Scan.builder()
                .id(entity.getId())
                .name(entity.getName())
                .scopeType(entity.getScopeType())
                .providerIds(fromStringList(entity.getProviderIds()))
                .environmentIds(fromStringList(entity.getEnvironmentIds()))
                .accountIds(fromStringList(entity.getAccountIds()))
                .regions(fromStringList(entity.getRegions()))
                .services(fromStringList(entity.getServices()))
                .status(entity.getStatus())
                .progressPercent(entity.getProgressPercent())
                .accountsTotal(entity.getAccountsTotal())
                .accountsCompleted(entity.getAccountsCompleted())
                .certificatesDiscovered(entity.getCertificatesDiscovered())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String toListString(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        return String.join(",", list);
    }

    private List<String> fromStringList(String str) {
        if (str == null || str.isEmpty()) return null;
        return Arrays.asList(str.split(","));
    }
}
