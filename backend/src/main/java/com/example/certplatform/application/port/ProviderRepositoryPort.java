package com.example.certplatform.application.port;

import com.example.certplatform.domain.model.Provider;
import java.util.List;
import java.util.Optional;

public interface ProviderRepositoryPort {
    Provider save(Provider provider);
    Optional<Provider> findById(String id);
    List<Provider> findByOrganisationId(String organisationId);
    void deleteById(String id);
    long count();
}
