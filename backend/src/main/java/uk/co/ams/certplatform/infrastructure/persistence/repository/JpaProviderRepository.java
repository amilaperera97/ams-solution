package uk.co.ams.certplatform.infrastructure.persistence.repository;

import uk.co.ams.certplatform.infrastructure.persistence.entity.ProviderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JpaProviderRepository extends JpaRepository<ProviderEntity, String> {
    List<ProviderEntity> findByOrganisationId(String organisationId);
}
