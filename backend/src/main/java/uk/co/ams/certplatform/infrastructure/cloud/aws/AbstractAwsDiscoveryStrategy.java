package uk.co.ams.certplatform.infrastructure.cloud.aws;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Certificate;
import uk.co.ams.certplatform.domain.model.CertificateUsage;
import uk.co.ams.certplatform.domain.model.DiscoveryResult;
import uk.co.ams.certplatform.domain.model.ScanContext;
import uk.co.ams.certplatform.infrastructure.discovery.support.AbstractDiscoveryStrategy;
import uk.co.ams.certplatform.infrastructure.discovery.support.SimulatedCertificateFactory;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Base class for AWS discovery strategies. Adds region resolution, client
 * construction and - most usefully - a shared reading of what an AWS failure
 * actually means.
 *
 * <p>The distinction between "denied" and "broken" matters: a scan run with a
 * read-only policy that omits one service should report that service as SKIPPED
 * with the permissions it needs, not fail the whole scan. Getting that wrong
 * once per strategy is how a fifteen-service scanner becomes untrustworthy.
 */
public abstract class AbstractAwsDiscoveryStrategy extends AbstractDiscoveryStrategy {

    /** Error codes AWS uses across services to mean "your policy does not allow this". */
    private static final Set<String> DENIED_CODES = Set.of(
            "ACCESSDENIED", "ACCESSDENIEDEXCEPTION", "UNAUTHORIZEDOPERATION", "AUTHFAILURE",
            "FORBIDDEN", "NOTAUTHORIZED", "INVALIDCLIENTTOKENID", "OPTINREQUIRED",
            "UNRECOGNIZEDCLIENTEXCEPTION");

    private static final Set<String> THROTTLE_CODES = Set.of(
            "THROTTLING", "THROTTLINGEXCEPTION", "THROTTLEDEXCEPTION", "REQUESTLIMITEXCEEDED",
            "TOOMANYREQUESTSEXCEPTION", "REQUESTTHROTTLED", "SLOWDOWN", "LIMITEXCEEDEDEXCEPTION");

    protected final AwsClientFactory clientFactory;

    protected AbstractAwsDiscoveryStrategy(CloudProviderProperties providerProperties,
                                           SimulatedCertificateFactory simulator,
                                           AwsClientFactory clientFactory) {
        super(providerProperties, simulator);
        this.clientFactory = clientFactory;
    }

    @Override
    protected String effectiveRegion(ScanContext context) {
        return clientFactory.resolveRegion(context.account(), context.region());
    }

    /** {@code try (var elb = client(ElasticLoadBalancingV2Client::builder, context))} */
    protected <B extends AwsClientBuilder<B, C>, C> C client(Supplier<B> builderSupplier, ScanContext context) {
        return clientFactory.client(builderSupplier, context.account(), effectiveRegion(context));
    }

    @Override
    protected void handleLiveFailure(ScanContext context, DiscoveryResult.Accumulator result, Exception e) {
        String code = errorCodeOf(e);
        String accountId = context.accountId() != null ? context.accountId() : "unknown";

        if (isDenied(code, e)) {
            // Not a fault: the credentials simply are not allowed to read this service.
            log.info("{} discovery skipped for account {} in {}: access denied",
                    descriptor().key(), accountId, result.region());
            result.skipped("The account's credentials are not allowed to read "
                    + descriptor().label() + "." + permissionHint());
            return;
        }
        if (isThrottled(code)) {
            log.warn("{} discovery throttled for account {} in {}", descriptor().key(), accountId, result.region());
            result.addError(rootMessage(e));
            result.partial("AWS throttled " + descriptor().label()
                    + " discovery; results for this region may be incomplete.");
            return;
        }
        log.warn("{} discovery failed for account {} in {}: {}",
                descriptor().key(), accountId, result.region(), e.getMessage());
        result.failed(rootMessage(e));
    }

    protected static String errorCodeOf(Throwable t) {
        Throwable cause = t;
        while (cause != null) {
            if (cause instanceof AwsServiceException aws && aws.awsErrorDetails() != null
                    && aws.awsErrorDetails().errorCode() != null) {
                return aws.awsErrorDetails().errorCode().toUpperCase(Locale.ROOT);
            }
            cause = cause.getCause() != cause ? cause.getCause() : null;
        }
        return "";
    }

    protected static boolean isDenied(String code, Throwable t) {
        if (DENIED_CODES.contains(code)) return true;
        String message = String.valueOf(rootMessage(t)).toLowerCase(Locale.ROOT);
        return message.contains("not authorized") || message.contains("access denied")
                || message.contains("explicit deny");
    }

    protected static boolean isThrottled(String code) {
        return THROTTLE_CODES.contains(code);
    }

    /** True when a per-resource call failed only because we may not read that resource. */
    protected boolean isDenied(SdkException e) {
        return isDenied(errorCodeOf(e), e);
    }

    // --- shared certificate assembly ----------------------------------------

    /** A certificate builder stamped with this task's provenance; callers fill in the rest. */
    protected Certificate.Builder newCertificate(ScanContext context, String sourceType) {
        return Certificate.builder()
                .id("cert-" + UUID.randomUUID())
                .provider(CloudProviderType.AWS.name())
                .accountId(context.accountId())
                .region(effectiveRegion(context))
                .service(descriptor().key())
                .sourceType(sourceType)
                .createdAt(Instant.now());
    }

    /**
     * Records that a certificate is attached to a resource. This is what turns a
     * flat certificate inventory into "which load balancer breaks when this expires".
     */
    protected CertificateUsage usage(ScanContext context, String service, String resourceArn,
                                     String resourceType, String usageType) {
        return CertificateUsage.builder()
                .service(service)
                .resource(resourceArn)
                .resourceType(resourceType)
                .usageType(usageType)
                .region(effectiveRegion(context))
                .account(context.accountId())
                .build();
    }

    /** Stops a runaway account from filling the database; paired with the per-service cap. */
    protected boolean overResourceLimit(ScanContext context, int seen, DiscoveryResult.Accumulator result) {
        int limit = context.options().maxResourcesPerService();
        if (seen < limit) return false;
        result.addError("Stopped after " + limit + " resources (certificate-discovery.options.max-resources-per-service)");
        return true;
    }

    protected static List<String> nonNull(List<String> values) {
        return values == null ? List.of() : values;
    }
}
