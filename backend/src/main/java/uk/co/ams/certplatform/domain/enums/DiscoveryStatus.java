package uk.co.ams.certplatform.domain.enums;

/**
 * Outcome of a single discovery task (one account x one region x one service).
 *
 * <p>The distinction between the three non-success values matters when the scan
 * state is aggregated: only {@link #FAILED} means something went wrong. A service
 * that is deliberately not wired up yet, or one the credentials are not allowed to
 * read, should not turn an otherwise healthy scan red.
 */
public enum DiscoveryStatus {
    /** Every resource in scope was read. */
    SUCCESS,
    /** Some resources were read, some could not be (throttling, per-resource denial). */
    PARTIAL,
    /** Nothing could be read; the service call itself failed. */
    FAILED,
    /** Deliberately not attempted - service disabled by configuration, or credentials lack access. */
    SKIPPED,
    /** No live implementation exists for this service yet. */
    NOT_IMPLEMENTED;

    public boolean isFailure() {
        return this == FAILED;
    }

    /** SKIPPED and NOT_IMPLEMENTED are neither a success nor a failure - they are "did not run". */
    public boolean ran() {
        return this == SUCCESS || this == PARTIAL || this == FAILED;
    }
}
