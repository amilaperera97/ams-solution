package uk.co.ams.certplatform.domain.model;

import java.time.Instant;

/** A deployment stage (prod, staging, ...) that accounts are grouped under. */
public record Environment(
        String id,
        String organisationId,
        String providerId,
        String name,
        String description,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public Environment withId(String id) {
        return toBuilder().id(id).build();
    }

    public Environment withStatus(String status) {
        return toBuilder().status(status).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Starting point for a modified copy - the stand-in for the setters this used to have. */
    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .organisationId(organisationId)
                .providerId(providerId)
                .name(name)
                .description(description)
                .status(status)
                .createdAt(createdAt)
                .updatedAt(updatedAt);
    }

    public static final class Builder {
        private String id;
        private String organisationId;
        private String providerId;
        private String name;
        private String description;
        private String status;
        private Instant createdAt;
        private Instant updatedAt;

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder organisationId(String organisationId) { this.organisationId = organisationId; return this; }
        public Builder providerId(String providerId) { this.providerId = providerId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public Environment build() {
            return new Environment(id, organisationId, providerId, name, description, status,
                    createdAt, updatedAt);
        }
    }
}
