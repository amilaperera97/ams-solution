package com.example.certplatform.domain.model;

import com.example.certplatform.domain.enums.CloudProviderType;
import java.time.Instant;

public class Provider {
    private String id;
    private String name;
    private String organisationId;
    private CloudProviderType type;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    public Provider() {
    }

    public Provider(String id, String name, String organisationId, CloudProviderType type, String status, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.organisationId = organisationId;
        this.type = type;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getOrganisationId() { return organisationId; }
    public void setOrganisationId(String organisationId) { this.organisationId = organisationId; }

    public CloudProviderType getType() { return type; }
    public void setType(CloudProviderType type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
