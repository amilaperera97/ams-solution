package uk.co.ams.certplatform.infrastructure.compute;

import uk.co.ams.certplatform.application.port.ComputeCertificateSource;
import uk.co.ams.certplatform.application.port.ComputeExecutor;
import uk.co.ams.certplatform.domain.enums.OsFamily;
import uk.co.ams.certplatform.domain.model.Certificate;
import uk.co.ams.certplatform.domain.model.CertificateUsage;
import uk.co.ams.certplatform.domain.model.ComputeCommand;
import uk.co.ams.certplatform.domain.model.ComputeCommandResult;
import uk.co.ams.certplatform.domain.model.ComputeTarget;
import uk.co.ams.certplatform.domain.model.DiscoveryResult;
import uk.co.ams.certplatform.domain.model.ResourceTag;
import uk.co.ams.certplatform.domain.model.ScanContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reads certificates off the disks of the machines in an account.
 *
 * <p>Sits between the transport ({@link ComputeExecutor}) and the formats
 * ({@link ComputeCertificateSource}) so neither has to know about the other, and
 * so every compute-side service - EC2 today, ECS and EKS on EC2 later, keystore
 * discovery after that - shares one implementation of the awkward parts:
 * grouping targets by platform, batching, and turning a failed instance into a
 * recorded gap rather than a failed scan.
 */
@Component
public class ComputeCertificateScanner {

    private static final Logger log = LoggerFactory.getLogger(ComputeCertificateScanner.class);

    private final List<ComputeExecutor> executors;
    private final List<ComputeCertificateSource> sources;

    public ComputeCertificateScanner(List<ComputeExecutor> executors, List<ComputeCertificateSource> sources) {
        this.executors = executors;
        this.sources = sources;
    }

    /**
     * Scans every reachable machine and adds what it finds to {@code result},
     * which is left in a terminal state on return.
     *
     * @param serviceKey   catalogue key of the calling strategy, stamped onto certificates
     * @param resourceType label for the usage record, e.g. "EC2 instance"
     */
    public void scan(ScanContext context, DiscoveryResult.Accumulator result, String serviceKey, String resourceType) {
        if (!context.options().scanComputeFilesystems()) {
            result.skipped("Compute filesystem scanning is disabled "
                    + "(certificate-discovery.options.scan-compute-filesystems)");
            return;
        }

        Optional<ComputeExecutor> executor = executors.stream()
                .filter(candidate -> candidate.supports(context.provider()))
                .findFirst();
        if (executor.isEmpty()) {
            result.notImplemented("No compute executor is available for " + context.provider()
                    + "; certificates on disk cannot be read.");
            return;
        }

        List<ComputeTarget> targets = executor.get().listTargets(context);
        if (targets.isEmpty()) {
            // Genuinely different from a failure: nothing was reachable, so nothing was missed.
            result.succeeded("No agent-managed instances were reachable in " + result.region());
            return;
        }

        Map<OsFamily, List<ComputeTarget>> byPlatform = groupByPlatform(targets);
        int scanned = 0;

        for (ComputeCertificateSource source : sources) {
            for (Map.Entry<OsFamily, List<ComputeTarget>> group : byPlatform.entrySet()) {
                if (!source.supports(group.getKey())) continue;

                ComputeCommand command = source.command(context, group.getKey());
                List<ComputeCommandResult> outcomes = executor.get().run(context, group.getValue(), command);

                for (ComputeCommandResult outcome : outcomes) {
                    scanned++;
                    collect(context, result, source, outcome, serviceKey, resourceType);
                }
            }
        }

        result.settle("Scanned " + targets.size() + " instance(s) in " + result.region()
                + " across " + scanned + " check(s)");
    }

    private void collect(ScanContext context, DiscoveryResult.Accumulator result, ComputeCertificateSource source,
                         ComputeCommandResult outcome, String serviceKey, String resourceType) {
        ComputeTarget target = outcome.target();

        if (!outcome.succeeded()) {
            // One unreachable box is a coverage gap worth reporting, not a scan failure.
            log.debug("{} on {} did not complete: {}", source.name(), target.id(), outcome.stderr());
            result.addError(source.name() + " on " + target.id() + ": " + summarise(outcome.stderr()));
            return;
        }
        if (outcome.truncated()) {
            result.addError(source.name() + " on " + target.id()
                    + ": output was truncated, some certificates may be missing");
        }

        for (Certificate certificate : source.parse(context, outcome)) {
            result.addCertificate(stamp(context, certificate, target, serviceKey, resourceType));
        }
    }

    /** Gives a certificate found on disk the provenance every other source already has. */
    private Certificate stamp(ScanContext context, Certificate certificate, ComputeTarget target,
                              String serviceKey, String resourceType) {
        CertificateUsage usage = CertificateUsage.builder()
                .service(serviceKey)
                .resource(target.resourceArn() != null ? target.resourceArn() : target.id())
                .resourceType(resourceType)
                .usageType("ON_DISK")
                .region(context.region())
                .account(context.accountId())
                .os(target.os())
                .build();

        List<ResourceTag> tags = new ArrayList<>();
        tags.add(new ResourceTag("compute:instanceId", target.id()));
        if (target.name() != null) tags.add(new ResourceTag("compute:name", target.displayName()));
        tags.addAll(target.tags());

        return certificate.toBuilder()
                .id(certificate.id() != null ? certificate.id() : "cert-" + UUID.randomUUID())
                .provider(context.provider().name())
                .accountId(context.accountId())
                .region(context.region())
                .service(serviceKey)
                .createdAt(Instant.now())
                // Nothing on a filesystem renews itself; that is the point of finding them.
                .autoRenewal(false)
                .usage(usage)
                .tags(concat(certificate.tags(), tags))
                .build();
    }

    private static List<ResourceTag> concat(List<ResourceTag> existing, List<ResourceTag> extra) {
        List<ResourceTag> combined = new ArrayList<>(existing);
        combined.addAll(extra);
        return combined;
    }

    private Map<OsFamily, List<ComputeTarget>> groupByPlatform(List<ComputeTarget> targets) {
        Map<OsFamily, List<ComputeTarget>> byPlatform = new EnumMap<>(OsFamily.class);
        for (ComputeTarget target : targets) {
            byPlatform.computeIfAbsent(target.osFamily(), family -> new ArrayList<>()).add(target);
        }
        // An unidentified platform gets the Unix treatment; on AWS that covers the
        // Linux variants SSM does not name, and a Windows box simply returns nothing.
        List<ComputeTarget> unknown = byPlatform.remove(OsFamily.UNKNOWN);
        if (unknown != null) {
            byPlatform.computeIfAbsent(OsFamily.LINUX, family -> new ArrayList<>()).addAll(unknown);
        }
        return byPlatform;
    }

    private static String summarise(String stderr) {
        if (stderr == null || stderr.isBlank()) return "no output";
        String single = stderr.replaceAll("\\s+", " ").trim();
        return single.length() <= 200 ? single : single.substring(0, 200) + "...";
    }
}
