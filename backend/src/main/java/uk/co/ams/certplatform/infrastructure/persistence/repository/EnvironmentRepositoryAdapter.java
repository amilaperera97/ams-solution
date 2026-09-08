package uk.co.ams.certplatform.infrastructure.persistence.repository;

import uk.co.ams.certplatform.application.port.EnvironmentRepositoryPort;
import uk.co.ams.certplatform.domain.model.Environment;
import uk.co.ams.certplatform.infrastructure.persistence.entity.EnvironmentEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class EnvironmentRepositoryAdapter implements EnvironmentRepositoryPort {

    private final JpaEnvironmentRepository jpaRepository;

    public EnvironmentRepositoryAdapter(JpaEnvironmentRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Environment save(Environment environment) {
        EnvironmentEntity entity = toEntity(environment);
        EnvironmentEntity savedEntity = jpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public Optional<Environment> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Environment> findByProviderId(String providerId) {
        return jpaRepository.findByProviderId(providerId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    private EnvironmentEntity toEntity(Environment domain) {
        if (domain == null) return null;
        EnvironmentEntity entity = new EnvironmentEntity();
        entity.setId(domain.getId());
        entity.setOrganisationId(domain.getOrganisationId());
        entity.setProviderId(domain.getProviderId());
        entity.setName(domain.getName());
        entity.setDescription(domain.getDescription());
        entity.setStatus(domain.getStatus());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }

    private Environment toDomain(EnvironmentEntity entity) {
        if (entity == null) return null;
        return new Environment(
                entity.getId(),
                entity.getOrganisationId(),
                entity.getProviderId(),
                entity.getName(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
