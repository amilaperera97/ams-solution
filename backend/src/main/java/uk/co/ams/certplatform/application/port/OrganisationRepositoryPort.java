package com.example.certplatform.application.port;

import com.example.certplatform.domain.model.Organisation;
import java.util.Optional;
import java.util.List;

public interface OrganisationRepositoryPort {
    Organisation save(Organisation organisation);
    Optional<Organisation> findById(String id);
    List<Organisation> findAll();
    void deleteById(String id);
}
