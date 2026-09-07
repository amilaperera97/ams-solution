package com.example.certplatform.application.port;

import com.example.certplatform.domain.model.Environment;
import java.util.List;
import java.util.Optional;

public interface EnvironmentRepositoryPort {
    Environment save(Environment environment);
    Optional<Environment> findById(String id);
    List<Environment> findByProviderId(String providerId);
    void deleteById(String id);
    long count();
}
