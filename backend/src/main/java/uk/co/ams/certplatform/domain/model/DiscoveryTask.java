package uk.co.ams.certplatform.domain.model;

/**
 * One unit of scan work: read this service, in this region, for this account.
 *
 * <p>The planner produces the full list up front so the executor can size,
 * order and bound the fan-out without knowing anything about services.
 */
public record DiscoveryTask(Account account, DiscoveryServiceDescriptor descriptor, String region) {

    @Override
    public String toString() {
        return descriptor.qualifiedKey() + "@" + region + " (account " + account.getId() + ")";
    }
}
