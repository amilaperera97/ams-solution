package uk.co.ams.certplatform.domain.model;

import uk.co.ams.certplatform.domain.enums.DiscoveryStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * What one discovery task found. Carries its own provenance so a result can be
 * attributed to an account, region and service after the fan-out has collapsed.
 *
 * <p>A finished result is immutable. Strategies build one up through
 * {@link Accumulator}, which collects certificates and per-resource errors and
 * is turned into a result by whichever terminal helper the strategy calls.
 */
public record DiscoveryResult(
        String provider,
        String accountId,
        String region,
        String service,
        DiscoveryStatus status,
        String message,
        List<Certificate> certificates,
        List<String> errors
) {

    public DiscoveryResult {
        status = status != null ? status : DiscoveryStatus.SUCCESS;
        certificates = certificates == null ? List.of() : List.copyOf(certificates);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /** An accumulator with no provenance, for tests and callers that do not have a task. */
    public static Accumulator accumulator() {
        return new Accumulator(null, null, null, null);
    }

    public static Accumulator accumulator(String provider, String accountId, String region, String service) {
        return new Accumulator(provider, accountId, region, service);
    }

    public static Accumulator forTask(DiscoveryTask task) {
        return new Accumulator(
                task.descriptor().provider().name(),
                task.account().id(),
                task.region(),
                task.descriptor().key());
    }

    /**
     * Mutable collector handed to a strategy while it reads a service.
     *
     * <p>Each terminal helper stamps a status and returns the finished, immutable
     * result, so a strategy that calls {@code settle(...)} last needs no other
     * ceremony to report honestly.
     */
    public static final class Accumulator {

        private final String provider;
        private final String accountId;
        private final String region;
        private final String service;

        private final List<Certificate> certificates = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        private DiscoveryStatus status = DiscoveryStatus.SUCCESS;
        private String message;

        private Accumulator(String provider, String accountId, String region, String service) {
            this.provider = provider;
            this.accountId = accountId;
            this.region = region;
            this.service = service;
        }

        public String region() {
            return region;
        }

        public Accumulator addCertificate(Certificate certificate) {
            if (certificate != null) certificates.add(certificate);
            return this;
        }

        public Accumulator addCertificates(List<Certificate> found) {
            if (found != null) found.forEach(this::addCertificate);
            return this;
        }

        public Accumulator addError(String error) {
            if (error != null) errors.add(error);
            return this;
        }

        public DiscoveryResult succeeded(String message) {
            return build(DiscoveryStatus.SUCCESS, message);
        }

        /** Some resources were read and some were not; the scan is still worth keeping. */
        public DiscoveryResult partial(String message) {
            return build(DiscoveryStatus.PARTIAL, message);
        }

        public DiscoveryResult failed(String message) {
            addError(message);
            return build(DiscoveryStatus.FAILED, message);
        }

        public DiscoveryResult skipped(String message) {
            return build(DiscoveryStatus.SKIPPED, message);
        }

        public DiscoveryResult notImplemented(String message) {
            return build(DiscoveryStatus.NOT_IMPLEMENTED, message);
        }

        /**
         * Downgrades SUCCESS to PARTIAL once at least one resource-level error has been
         * recorded, so a strategy can keep going past a single unreadable resource and
         * still report honestly at the end.
         */
        public DiscoveryResult settle(String successMessage) {
            if (errors.isEmpty()) return succeeded(successMessage);
            return partial(successMessage + " (" + errors.size() + " resource(s) could not be read)");
        }

        /**
         * The result as it stands. A strategy reports through a terminal helper and
         * discards the value it returns, so the last state one recorded is what this
         * hands back; a strategy that set none is still a SUCCESS.
         */
        public DiscoveryResult build() {
            return new DiscoveryResult(provider, accountId, region, service, status, message,
                    certificates, errors);
        }

        private DiscoveryResult build(DiscoveryStatus status, String message) {
            this.status = status;
            this.message = message;
            return build();
        }
    }
}
