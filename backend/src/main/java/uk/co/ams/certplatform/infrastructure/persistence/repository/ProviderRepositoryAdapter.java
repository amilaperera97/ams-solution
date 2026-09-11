package uk.co.ams.certplatform.infrastructure.persistence.repository;

import uk.co.ams.certplatform.application.port.ProviderRepositoryPort;
import uk.co.ams.certplatform.domain.model.Provider;
import uk.co.ams.certplatform.infrastructure.persistence.entity.ProviderEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ProviderRepositoryAdapter implements ProviderRepositoryPort {

    private final JpaProviderRepository jpaRepository;

    public ProviderRepositoryAdapter(JpaProviderRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Provider save(Provider provider) {
        ProviderEntity entity = toEntity(provider);
        ProviderEntity savedEntity = jpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public Optional<Provider> findById(String id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Provider> findByOrganisationId(String organisationId) {
        return jpaRepository.findByOrganisationId(organisationId).stream()
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

    private ProviderEntity toEntity(Provider domain) {
        if (domain == null) return null;
        ProviderEntity entity = new ProviderEntity();
        entity.setId(domain.id());
        entity.setName(domain.name());
        entity.setOrganisationId(domain.organisationId());
        entity.setType(domain.type());
        entity.setStatus(domain.status());
        entity.setCreatedAt(domain.createdAt());
        entity.setUpdatedAt(domain.updatedAt());
        return entity;
    }

    private Provider toDomain(ProviderEntity entity) {
        if (entity == null) return null;
        return new Provider(
                entity.getId(),
                entity.getName(),
                entity.getOrganisationId(),
                entity.getType(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
