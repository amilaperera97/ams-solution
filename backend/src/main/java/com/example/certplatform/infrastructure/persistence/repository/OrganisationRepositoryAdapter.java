package com.example.certplatform.infrastructure.persistence.repository;

import com.example.certplatform.application.port.OrganisationRepositoryPort;
import com.example.certplatform.domain.model.Organisation;
import com.example.certplatform.infrastructure.persistence.entity.OrganisationEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class OrganisationRepositoryAdapter implements OrganisationRepositoryPort {

    private final JpaOrganisationRepository jpaRepository;

    public OrganisationRepositoryAdapter(JpaOrganisationRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Organisation save(Organisation organisation) {
        OrganisationEntity entity = toEntity(organisation);
        OrganisationEntity savedEntity = jpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public Optional<Organisation> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Organisation> findAll() {
        return jpaRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        jpaRepository.deleteById(id);
    }

    private OrganisationEntity toEntity(Organisation domain) {
        if (domain == null) return null;
        OrganisationEntity entity = new OrganisationEntity();
        entity.setId(domain.getId());
        entity.setName(domain.getName());
        entity.setDescription(domain.getDescription());
        entity.setStatus(domain.getStatus());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    private Organisation toDomain(OrganisationEntity entity) {
        if (entity == null) return null;
        return new Organisation(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
