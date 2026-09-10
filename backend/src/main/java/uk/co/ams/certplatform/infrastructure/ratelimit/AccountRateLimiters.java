package uk.co.ams.certplatform.infrastructure.ratelimit;

import uk.co.ams.certplatform.application.port.RateLimiter;
import uk.co.ams.certplatform.shared.config.DiscoveryProperties;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One rate limiter per account. Throttling budgets are per credential set, so
 * limiting globally would either starve a small estate or fail to protect a
 * large one.
 */
@Component
public class AccountRateLimiters {

    private final Map<String, RateLimiter> byAccountId = new ConcurrentHashMap<>();
    private final DiscoveryProperties properties;

    public AccountRateLimiters(DiscoveryProperties properties) {
        this.properties = properties;
    }

    public RateLimiter forAccount(String accountId) {
        return byAccountId.computeIfAbsent(accountId == null ? "unknown" : accountId,
                id -> new TokenBucketRateLimiter(properties.getParallelism().getTaskStartsPerSecondPerAccount()));
    }
}
