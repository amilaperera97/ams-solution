package uk.co.ams.certplatform.domain.model;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;

/**
 * Everything a discovery strategy is given for one unit of work.
 *
 * <p>Immutable, and always tied to exactly one account, one region and one
 * service. Strategies never see the wider scan, which is what keeps them
 * independently testable and safe to run in parallel.
 */
public final class ScanContext {

    private final String scanId;
    private final Account account;
    private final CloudProviderType provider;
    private final String region;
    private final String service;
    private final DiscoveryServiceDescriptor descriptor;
    private final DiscoveryOptions options;

    public ScanContext(String scanId, Account account, CloudProviderType provider, String region,
                       String service, DiscoveryServiceDescriptor descriptor, DiscoveryOptions options) {
        this.scanId = scanId;
        this.account = account;
        this.provider = provider;
        this.region = region;
        this.service = service;
        this.descriptor = descriptor;
        this.options = options != null ? options : DiscoveryOptions.defaults();
    }

    /** Convenience for callers outside the planner (connection tests, unit tests). */
    public ScanContext(String scanId, Account account, String region, String service) {
        this(scanId, account, CloudProviderType.AWS, region, service, null, DiscoveryOptions.defaults());
    }

    public static ScanContext forTask(String scanId, DiscoveryTask task, DiscoveryOptions options) {
        return new ScanContext(scanId, task.account(), task.descriptor().provider(), task.region(),
                task.descriptor().key(), task.descriptor(), options);
    }

    public String getScanId() { return scanId; }
    public Account getAccount() { return account; }
    public CloudProviderType getProvider() { return provider; }
    public String getRegion() { return region; }
    public String getService() { return service; }
    public DiscoveryServiceDescriptor getDescriptor() { return descriptor; }
    public DiscoveryOptions getOptions() { return options; }

    @Override
    public String toString() {
        return "ScanContext[scan=" + scanId + ", account=" + (account != null ? account.getId() : null)
                + ", region=" + region + ", service=" + service + "]";
    }
}
