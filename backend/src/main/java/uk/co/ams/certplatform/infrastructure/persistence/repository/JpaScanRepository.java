package uk.co.ams.certplatform.infrastructure.persistence.repository;

import uk.co.ams.certplatform.infrastructure.persistence.entity.ScanEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaScanRepository extends JpaRepository<ScanEntity, String> {
}
