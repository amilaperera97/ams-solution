package uk.co.ams.certplatform.domain.model;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;

/**
 * Everything a discovery strategy is given for one unit of work.
 *
 * <p>Always tied to exactly one account, one region and one service. Strategies
 * never see the wider scan, which is what keeps them independently testable and
 * safe to run in parallel.
 */
public record ScanContext(
        String scanId,
        Account account,
        CloudProviderType provider,
        String region,
        String service,
        DiscoveryServiceDescriptor descriptor,
        DiscoveryOptions options
) {

    public ScanContext {
        options = options != null ? options : DiscoveryOptions.defaults();
    }

    /** Convenience for callers outside the planner (connection tests, unit tests). */
    public ScanContext(String scanId, Account account, String region, String service) {
        this(scanId, account, CloudProviderType.AWS, region, service, null, DiscoveryOptions.defaults());
    }

    public static ScanContext forTask(String scanId, DiscoveryTask task, DiscoveryOptions options) {
        return new ScanContext(scanId, task.account(), task.descriptor().provider(), task.region(),
                task.descriptor().key(), task.descriptor(), options);
    }

    /** The account id, or null when the context has no account - saves callers a null check. */
    public String accountId() {
        return account != null ? account.id() : null;
    }

    @Override
    public String toString() {
        return "ScanContext[scan=" + scanId + ", account=" + accountId()
                + ", region=" + region + ", service=" + service + "]";
    }
}
