package uk.co.ams.certplatform.infrastructure.ratelimit;

import uk.co.ams.certplatform.application.port.RateLimiter;

import java.util.concurrent.locks.LockSupport;

/**
 * A plain token bucket: {@code permitsPerSecond} refilled continuously, with a
 * small burst allowance so short bursts pass and sustained load does not.
 *
 * <p>Deliberately small and dependency-free. The AWS SDK already retries
 * throttled calls with adaptive backoff; this sits one level up and stops the
 * platform generating the throttling in the first place.
 *
 * <p>Waiters reserve a permit and then sleep for the debt they inherited, so
 * concurrent callers stagger instead of all waking at the same instant.
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private final double permitsPerSecond;
    private final double maxBurst;

    private double availablePermits;
    private long lastRefillNanos;

    public TokenBucketRateLimiter(double permitsPerSecond) {
        this(permitsPerSecond, Math.max(1.0, permitsPerSecond));
    }

    public TokenBucketRateLimiter(double permitsPerSecond, double maxBurst) {
        if (permitsPerSecond <= 0) throw new IllegalArgumentException("permitsPerSecond must be positive");
        this.permitsPerSecond = permitsPerSecond;
        this.maxBurst = maxBurst;
        this.availablePermits = maxBurst;
        this.lastRefillNanos = System.nanoTime();
    }

    @Override
    public void acquire() {
        long parkNanos;
        synchronized (this) {
            refill();
            if (availablePermits >= 1.0) {
                availablePermits -= 1.0;
                return;
            }
            parkNanos = (long) ((1.0 - availablePermits) / permitsPerSecond * 1_000_000_000L);
            availablePermits -= 1.0;
        }
        // Sleep outside the lock so other callers can still reserve their turn.
        if (parkNanos > 0) LockSupport.parkNanos(parkNanos);
    }

    private void refill() {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
        lastRefillNanos = now;
        availablePermits = Math.min(maxBurst, availablePermits + elapsedSeconds * permitsPerSecond);
    }
}
