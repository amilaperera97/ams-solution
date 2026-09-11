package uk.co.ams.certplatform.domain.model;

import uk.co.ams.certplatform.domain.enums.ScanScopeType;
import uk.co.ams.certplatform.domain.enums.ScanState;

import java.time.Instant;
import java.util.List;

/**
 * One requested run of discovery, and the progress counters the UI polls.
 *
 * <p>Immutable: every state change returns a new {@code Scan}, so the executor
 * cannot leave a half-updated scan visible to the status endpoint while it is
 * still working. {@link #transitionTo} carries the state machine, so an illegal
 * move fails at the point it is attempted rather than at the point it is read.
 */
public record Scan(
        String id,
        String name,
        ScanScopeType scopeType,
        List<String> providerIds,
        List<String> environmentIds,
        List<String> accountIds,
        List<String> regions,
        List<String> services,
        ScanState status,
        int progressPercent,
        int accountsTotal,
        int accountsCompleted,
        int certificatesDiscovered,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Returns a copy in {@code newState}, refusing the moves the state machine
     * does not allow.
     *
     * @throws IllegalStateException if this scan has already finished, or the move
     *                               skips the work a scan has to do to complete
     */
    public Scan transitionTo(ScanState newState) {
        if (status == ScanState.COMPLETED || status == ScanState.CANCELLED || status == ScanState.FAILED) {
            throw new IllegalStateException("Cannot transition from final state " + status + " to " + newState);
        }
        if (status == ScanState.QUEUED && newState == ScanState.COMPLETED) {
            throw new IllegalStateException("Cannot transition directly from QUEUED to COMPLETED");
        }
        return withStatus(newState);
    }

    public Scan withId(String id) {
        return toBuilder().id(id).build();
    }

    public Scan withStatus(ScanState status) {
        return toBuilder().status(status).build();
    }

    public Scan withAccountsTotal(int accountsTotal) {
        return toBuilder().accountsTotal(accountsTotal).build();
    }

    /** Records the end-of-run counters in one step, so they can never disagree. */
    public Scan withProgress(int progressPercent, int accountsCompleted, int certificatesDiscovered) {
        return toBuilder()
                .progressPercent(progressPercent)
                .accountsCompleted(accountsCompleted)
                .certificatesDiscovered(certificatesDiscovered)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Starting point for a modified copy - the stand-in for the setters this used to have. */
    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .name(name)
                .scopeType(scopeType)
                .providerIds(providerIds)
                .environmentIds(environmentIds)
                .accountIds(accountIds)
                .regions(regions)
                .services(services)
                .status(status)
                .progressPercent(progressPercent)
                .accountsTotal(accountsTotal)
                .accountsCompleted(accountsCompleted)
                .certificatesDiscovered(certificatesDiscovered)
                .createdAt(createdAt)
                .updatedAt(updatedAt);
    }

    public static final class Builder {
        private String id;
        private String name;
        private ScanScopeType scopeType;
        private List<String> providerIds;
        private List<String> environmentIds;
        private List<String> accountIds;
        private List<String> regions;
        private List<String> services;
        private ScanState status;
        private int progressPercent;
        private int accountsTotal;
        private int accountsCompleted;
        private int certificatesDiscovered;
        private Instant createdAt;
        private Instant updatedAt;

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder scopeType(ScanScopeType scopeType) { this.scopeType = scopeType; return this; }
        public Builder providerIds(List<String> providerIds) { this.providerIds = providerIds; return this; }
        public Builder environmentIds(List<String> environmentIds) { this.environmentIds = environmentIds; return this; }
        public Builder accountIds(List<String> accountIds) { this.accountIds = accountIds; return this; }
        public Builder regions(List<String> regions) { this.regions = regions; return this; }
        public Builder services(List<String> services) { this.services = services; return this; }
        public Builder status(ScanState status) { this.status = status; return this; }
        public Builder progressPercent(int progressPercent) { this.progressPercent = progressPercent; return this; }
        public Builder accountsTotal(int accountsTotal) { this.accountsTotal = accountsTotal; return this; }
        public Builder accountsCompleted(int accountsCompleted) { this.accountsCompleted = accountsCompleted; return this; }
        public Builder certificatesDiscovered(int certificatesDiscovered) { this.certificatesDiscovered = certificatesDiscovered; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }

        public Scan build() {
            return new Scan(id, name, scopeType, providerIds, environmentIds, accountIds, regions, services,
                    status, progressPercent, accountsTotal, accountsCompleted, certificatesDiscovered,
                    createdAt, updatedAt);
        }
    }
}
