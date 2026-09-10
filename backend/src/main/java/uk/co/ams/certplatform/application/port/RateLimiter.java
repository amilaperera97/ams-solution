package uk.co.ams.certplatform.application.port;

/**
 * Bounds the rate at which discovery tasks are started against one account.
 *
 * <p>A fifteen-service scan over five regions fires seventy-five task bursts at
 * a single set of credentials. Cloud APIs answer that with throttling, and a
 * throttled scan looks identical to a broken one. Acquiring a permit before each
 * task keeps the burst inside the provider's budget.
 */
public interface RateLimiter {

    /** Blocks until a permit is available. */
    void acquire();
}
