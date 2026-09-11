package uk.co.ams.certplatform.domain.model;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;

import java.time.Instant;

/** One cloud (AWS, Azure, GCP) registered against an organisation. */
public record Provider(
        String id,
        String name,
        String organisationId,
        CloudProviderType type,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public Provider withId(String id) {
        return toBuilder().id(id).build();
    }

    public Provider withStatus(String status) {
        return toBuilder().status(status).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Starting point for a modified copy - the stand-in for the setters this used to have. */
    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .name(name)
                .organisationId(organisationId)
                .type(type)
                .status(status)
                .createdAt(createdAt)
                .updatedAt(updatedAt);
    }

    public static final class Builder {
        private String id;
        private String name;
        private String organisationId;
        private CloudProviderType type;
        private String status;
        private Instant createdAt;
        private Instant updatedAt;

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder organisationId(String organisationId) { this.organisationId = organisationId; return this; }
        public Builder type(CloudProviderType type) { this.type = type; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public Provider build() {
            return new Provider(id, name, organisationId, type, status, createdAt, updatedAt);
        }
    }
}
