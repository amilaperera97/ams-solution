package uk.co.ams.certplatform.application.port;

import uk.co.ams.certplatform.domain.model.Scan;
import java.util.List;
import java.util.Optional;

public interface ScanRepositoryPort {
    Scan save(Scan scan);
    Optional<Scan> findById(String id);
    List<Scan> findAll();
}
