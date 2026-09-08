package com.example.certplatform.application.port;

import com.example.certplatform.domain.model.Scan;
import java.util.List;
import java.util.Optional;

public interface ScanRepositoryPort {
    Scan save(Scan scan);
    Optional<Scan> findById(String id);
    List<Scan> findAll();
}
