package uk.co.ams.certplatform.domain.model;

import java.time.Instant;

public class Environment {
    private String id;
    private String organisationId;
    private String providerId;
    private String name;
    private String description;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public Environment() {
    }

    public Environment(String id, String organisationId, String providerId, String name, String description, String status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.organisationId = organisationId;
        this.providerId = providerId;
        this.name = name;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganisationId() { return organisationId; }
    public void setOrganisationId(String organisationId) { this.organisationId = organisationId; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
