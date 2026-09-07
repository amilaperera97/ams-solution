package com.example.certplatform.infrastructure.persistence.repository;

import com.example.certplatform.application.port.ScanRepositoryPort;
import com.example.certplatform.domain.model.Scan;
import com.example.certplatform.infrastructure.persistence.entity.ScanEntity;
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
        entity.setId(domain.getId());
        entity.setName(domain.getName());
        entity.setScopeType(domain.getScopeType());
        entity.setProviderIds(toListString(domain.getProviderIds()));
        entity.setEnvironmentIds(toListString(domain.getEnvironmentIds()));
        entity.setAccountIds(toListString(domain.getAccountIds()));
        entity.setRegions(toListString(domain.getRegions()));
        entity.setServices(toListString(domain.getServices()));
        entity.setStatus(domain.getStatus());
        entity.setProgressPercent(domain.getProgressPercent());
        entity.setAccountsTotal(domain.getAccountsTotal());
        entity.setAccountsCompleted(domain.getAccountsCompleted());
        entity.setCertificatesDiscovered(domain.getCertificatesDiscovered());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    private Scan toDomain(ScanEntity entity) {
        if (entity == null) return null;
        Scan scan = new Scan();
        scan.setId(entity.getId());
        scan.setName(entity.getName());
        scan.setScopeType(entity.getScopeType());
        scan.setProviderIds(fromStringList(entity.getProviderIds()));
        scan.setEnvironmentIds(fromStringList(entity.getEnvironmentIds()));
        scan.setAccountIds(fromStringList(entity.getAccountIds()));
        scan.setRegions(fromStringList(entity.getRegions()));
        scan.setServices(fromStringList(entity.getServices()));
        scan.setStatus(entity.getStatus());
        scan.setProgressPercent(entity.getProgressPercent());
        scan.setAccountsTotal(entity.getAccountsTotal());
        scan.setAccountsCompleted(entity.getAccountsCompleted());
        scan.setCertificatesDiscovered(entity.getCertificatesDiscovered());
        scan.setCreatedAt(entity.getCreatedAt());
        scan.setUpdatedAt(entity.getUpdatedAt());
        return scan;
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
