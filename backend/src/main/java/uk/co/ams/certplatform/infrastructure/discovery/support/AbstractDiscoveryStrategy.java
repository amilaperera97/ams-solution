package uk.co.ams.certplatform.infrastructure.discovery.support;

import uk.co.ams.certplatform.application.port.CertificateDiscoveryStrategy;
import uk.co.ams.certplatform.domain.model.DiscoveryResult;
import uk.co.ams.certplatform.domain.model.ScanContext;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Everything every discovery strategy has to do, done once.
 *
 * <p>Builds the result with the right provenance, routes to the simulation when
 * the provider is in MOCK mode, keeps a thrown exception from escaping, and
 * enforces the rule that REAL mode never fabricates data. A concrete strategy is
 * left with one method: read the service and add what it finds.
 */
public abstract class AbstractDiscoveryStrategy implements CertificateDiscoveryStrategy {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final CloudProviderProperties providerProperties;
    protected final SimulatedCertificateFactory simulator;

    protected AbstractDiscoveryStrategy(CloudProviderProperties providerProperties,
                                        SimulatedCertificateFactory simulator) {
        this.providerProperties = providerProperties;
        this.simulator = simulator;
    }

    @Override
    public final DiscoveryResult discover(ScanContext context) {
        DiscoveryResult.Accumulator result = newResult(context);

        if (!descriptor().implemented()) {
            return result.notImplemented(descriptor().label()
                    + " discovery is registered but has no live implementation yet.");
        }

        if (!providerProperties.isReal(descriptor().provider())) {
            try {
                simulate(context, result);
            } catch (Exception e) {
                return result.failed("Simulation failed: " + rootMessage(e));
            }
            return result.build();
        }

        try {
            discoverLive(context, result);
        } catch (Exception e) {
            // REAL mode must report the failure, never fall back to invented data.
            handleLiveFailure(context, result, e);
        }
        return result.build();
    }

    /**
     * Read the live service and add whatever is found to {@code result}. Set a
     * terminal state with {@link DiscoveryResult.Accumulator#settle} (or one of the
     * explicit helpers); if nothing is set, the result stays SUCCESS.
     */
    protected abstract void discoverLive(ScanContext context, DiscoveryResult.Accumulator result) throws Exception;

    /**
     * MOCK-mode behaviour. The default produces one plausible certificate for the
     * service so the UI and the whole pipeline can be exercised without a cloud
     * account; override when a service needs a more specific shape.
     */
    protected void simulate(ScanContext context, DiscoveryResult.Accumulator result) {
        result.addCertificate(simulator.certificateFor(context, descriptor()));
        result.succeeded("Simulated 1 certificate for " + descriptor().label() + " (provider mode is MOCK)");
    }

    /** Maps a failure onto a status. Subclasses refine this per provider SDK. */
    protected void handleLiveFailure(ScanContext context, DiscoveryResult.Accumulator result, Exception e) {
        log.warn("{} discovery failed for account {} in {}: {}",
                descriptor().key(), context.accountId(), result.region(), e.getMessage());
        result.failed(rootMessage(e));
    }

    /** Region the strategy actually talked to; subclasses resolve defaults here. */
    protected String effectiveRegion(ScanContext context) {
        return context.region();
    }

    protected DiscoveryResult.Accumulator newResult(ScanContext context) {
        return DiscoveryResult.accumulator(
                descriptor().provider().name(),
                context.accountId(),
                effectiveRegion(context),
                descriptor().key());
    }

    /** Human-readable hint listing the permissions this service needs. */
    protected String permissionHint() {
        if (descriptor().requiredPermissions().isEmpty()) return "";
        return " Required permissions: " + String.join(", ", descriptor().requiredPermissions()) + ".";
    }

    protected static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }
}
