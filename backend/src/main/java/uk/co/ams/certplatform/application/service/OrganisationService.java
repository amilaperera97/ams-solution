package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.application.port.OrganisationRepositoryPort;
import uk.co.ams.certplatform.domain.model.Organisation;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrganisationService {

    private final OrganisationRepositoryPort repositoryPort;

    public OrganisationService(OrganisationRepositoryPort repositoryPort) {
        this.repositoryPort = repositoryPort;
    }

    public Organisation createOrganisation(String name, String description) {
        Organisation org = new Organisation();
        org.setId("org-" + UUID.randomUUID().toString());
        org.setName(name);
        org.setDescription(description);
        org.setStatus("ACTIVE");
        org.setCreatedAt(Instant.now());
        org.setUpdatedAt(Instant.now());
        
        return repositoryPort.save(org);
    }

    public Optional<Organisation> getOrganisation(String id) {
        return repositoryPort.findById(id);
    }

    public Optional<Organisation> getCurrentOrganisation() {
        return repositoryPort.findAll().stream().findFirst();
    }
}
