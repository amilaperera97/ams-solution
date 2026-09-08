package uk.co.ams.certplatform.application.port;

import uk.co.ams.certplatform.domain.model.Certificate;
import java.util.List;

public interface CertificateRepositoryPort {
    Certificate save(Certificate certificate);
    List<Certificate> findByScanId(String scanId);
    java.util.Optional<Certificate> findById(String id);
    long count();
    long countByStatus(String status);
}
