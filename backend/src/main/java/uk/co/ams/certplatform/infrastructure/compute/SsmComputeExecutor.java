package uk.co.ams.certplatform.infrastructure.compute;

import uk.co.ams.certplatform.application.port.ComputeExecutor;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.enums.OsFamily;
import uk.co.ams.certplatform.domain.model.ComputeCommand;
import uk.co.ams.certplatform.domain.model.ComputeCommandResult;
import uk.co.ams.certplatform.domain.model.ComputeTarget;
import uk.co.ams.certplatform.domain.model.OperatingSystemMetadata;
import uk.co.ams.certplatform.domain.model.ResourceTag;
import uk.co.ams.certplatform.domain.model.ScanContext;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsClientFactory;
import uk.co.ams.certplatform.shared.config.DiscoveryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.DescribeInstanceInformationRequest;
import software.amazon.awssdk.services.ssm.model.GetCommandInvocationRequest;
import software.amazon.awssdk.services.ssm.model.GetCommandInvocationResponse;
import software.amazon.awssdk.services.ssm.model.InstanceInformation;
import software.amazon.awssdk.services.ssm.model.InstanceInformationStringFilter;
import software.amazon.awssdk.services.ssm.model.InvocationDoesNotExistException;
import software.amazon.awssdk.services.ssm.model.SendCommandRequest;
import software.amazon.awssdk.services.ssm.model.SendCommandResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reaches EC2 instances through SSM Run Command.
 *
 * <p>Chosen over SSH because it needs no inbound ports, no stored keys and no
 * network path from the platform to the instance: authorisation is the same IAM
 * policy that governs the rest of the scan, and every command is logged in
 * CloudTrail. The cost is that only instances running the SSM agent with an
 * instance profile are reachable, which is why {@link #listTargets} reports what
 * it can see rather than what exists.
 *
 * <p>Run Command truncates captured output at roughly 24 KB. Scripts are written
 * to stay well inside that, and {@link ComputeCommandResult#truncated()} flags
 * the cases that do not, so a partial read is never mistaken for a clean one.
 */
@Component
public class SsmComputeExecutor implements ComputeExecutor {

    private static final Logger log = LoggerFactory.getLogger(SsmComputeExecutor.class);

    private static final String LINUX_DOCUMENT = "AWS-RunShellScript";
    private static final String WINDOWS_DOCUMENT = "AWS-RunPowerShellScript";
    /** SendCommand accepts at most 50 explicit instance ids. */
    private static final int SEND_BATCH_SIZE = 50;
    /** DescribeInstances accepts at most 200 ids in an instance-id filter. */
    private static final int DESCRIBE_BATCH_SIZE = 200;
    private static final int OUTPUT_LIMIT_BYTES = 24_000;
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private static final Set<String> TERMINAL_STATUSES =
            Set.of("Success", "Cancelled", "TimedOut", "Failed", "Cancelling");

    private final AwsClientFactory clientFactory;
    private final DiscoveryProperties properties;

    public SsmComputeExecutor(AwsClientFactory clientFactory, DiscoveryProperties properties) {
        this.clientFactory = clientFactory;
        this.properties = properties;
    }

    @Override
    public boolean supports(CloudProviderType provider) {
        return provider == CloudProviderType.AWS;
    }

    // --- discovering what we can reach --------------------------------------

    @Override
    public List<ComputeTarget> listTargets(ScanContext context) {
        int limit = properties.getCompute().getMaxTargetsPerRegion();
        Map<String, ComputeTarget> byInstanceId = new LinkedHashMap<>();

        try (SsmClient ssm = clientFactory.client(SsmClient::builder, context.getAccount(), context.getRegion())) {
            // Only Online instances are worth a command; anything else would just
            // sit in Pending until the invocation timed out.
            for (InstanceInformation information : ssm.describeInstanceInformationPaginator(
                    DescribeInstanceInformationRequest.builder()
                            .filters(InstanceInformationStringFilter.builder()
                                    .key("PingStatus").values("Online").build())
                            .build()).instanceInformationList()) {

                if (byInstanceId.size() >= limit) {
                    log.info("Reached the {} target cap for account {} in {}; remaining instances were not scanned",
                            limit, context.getAccount().getId(), context.getRegion());
                    break;
                }
                byInstanceId.put(information.instanceId(), toTarget(information));
            }
        } catch (SdkException e) {
            log.warn("Could not list SSM-managed instances for account {} in {}: {}",
                    context.getAccount().getId(), context.getRegion(), e.getMessage());
            return List.of();
        }

        enrichWithEc2Metadata(context, byInstanceId);
        return List.copyOf(byInstanceId.values());
    }

    private ComputeTarget toTarget(InstanceInformation information) {
        ComputeTarget target = new ComputeTarget(information.instanceId(),
                OsFamily.from(information.platformTypeAsString()));
        target.setService("EC2");
        target.setName(information.computerName());

        OperatingSystemMetadata os = new OperatingSystemMetadata();
        os.setFamily(String.valueOf(information.platformTypeAsString()).toUpperCase());
        os.setName(information.platformName());
        os.setVersion(information.platformVersion());
        target.setOs(os);
        return target;
    }

    /**
     * SSM knows the platform but not the tags, and tags are how an operator finds
     * the certificate again ("which application owns this?"). Fetched in bulk
     * rather than per instance.
     */
    private void enrichWithEc2Metadata(ScanContext context, Map<String, ComputeTarget> byInstanceId) {
        if (byInstanceId.isEmpty()) return;

        List<String> instanceIds = new ArrayList<>(byInstanceId.keySet());
        try (Ec2Client ec2 = clientFactory.client(Ec2Client::builder, context.getAccount(), context.getRegion())) {
            for (int start = 0; start < instanceIds.size(); start += DESCRIBE_BATCH_SIZE) {
                List<String> batch = instanceIds.subList(start,
                        Math.min(start + DESCRIBE_BATCH_SIZE, instanceIds.size()));

                for (Reservation reservation : ec2.describeInstances(DescribeInstancesRequest.builder()
                        .instanceIds(batch).build()).reservations()) {
                    for (Instance instance : reservation.instances()) {
                        ComputeTarget target = byInstanceId.get(instance.instanceId());
                        if (target == null) continue;
                        target.setResourceArn(instance.instanceId());
                        instance.tags().forEach(tag -> {
                            target.addTag(new ResourceTag(tag.key(), tag.value()));
                            if ("Name".equals(tag.key())) target.setName(tag.value());
                        });
                    }
                }
            }
        } catch (SdkException e) {
            // Tags are decoration; without ec2:DescribeInstances the scan still works.
            log.debug("Could not enrich instances with EC2 tags: {}", e.getMessage());
        }
    }

    // --- running the command -------------------------------------------------

    @Override
    public List<ComputeCommandResult> run(ScanContext context, List<ComputeTarget> targets, ComputeCommand command) {
        Map<String, ComputeCommandResult> byInstanceId = new HashMap<>();
        if (targets.isEmpty()) return List.of();

        try (SsmClient ssm = clientFactory.client(SsmClient::builder, context.getAccount(), context.getRegion())) {
            for (int start = 0; start < targets.size(); start += SEND_BATCH_SIZE) {
                List<ComputeTarget> batch = targets.subList(start,
                        Math.min(start + SEND_BATCH_SIZE, targets.size()));
                runBatch(ssm, batch, command, byInstanceId);
            }
        } catch (SdkException e) {
            log.warn("SSM Run Command failed for account {} in {}: {}",
                    context.getAccount().getId(), context.getRegion(), e.getMessage());
        }

        // One result per requested target, in the order asked for.
        List<ComputeCommandResult> results = new ArrayList<>(targets.size());
        for (ComputeTarget target : targets) {
            results.add(byInstanceId.getOrDefault(target.getId(),
                    ComputeCommandResult.failure(target, "No SSM invocation result was returned")));
        }
        return results;
    }

    private void runBatch(SsmClient ssm, List<ComputeTarget> batch, ComputeCommand command,
                          Map<String, ComputeCommandResult> byInstanceId) {
        List<String> instanceIds = batch.stream().map(ComputeTarget::getId).toList();
        String document = command.osFamily() == OsFamily.WINDOWS ? WINDOWS_DOCUMENT : LINUX_DOCUMENT;
        long timeoutSeconds = command.timeout().getSeconds();

        SendCommandResponse sent;
        try {
            sent = ssm.sendCommand(SendCommandRequest.builder()
                    .documentName(document)
                    .instanceIds(instanceIds)
                    .parameters(Map.of("commands", command.lines()))
                    .timeoutSeconds((int) Math.max(30, timeoutSeconds))
                    .comment("certplatform certificate discovery")
                    .build());
        } catch (SdkException e) {
            log.warn("SSM SendCommand rejected for {} instance(s): {}", instanceIds.size(), e.getMessage());
            batch.forEach(target -> byInstanceId.put(target.getId(),
                    ComputeCommandResult.failure(target, "SendCommand failed: " + e.getMessage())));
            return;
        }

        String commandId = sent.command().commandId();
        Instant deadline = Instant.now().plus(command.timeout());
        for (ComputeTarget target : batch) {
            byInstanceId.put(target.getId(), awaitInvocation(ssm, commandId, target, deadline));
        }
    }

    /**
     * Polls one invocation to completion. SSM has no blocking read, and the
     * built-in waiter treats a still-Pending invocation as a failure, so the loop
     * is explicit and honours the caller's deadline.
     */
    private ComputeCommandResult awaitInvocation(SsmClient ssm, String commandId,
                                                 ComputeTarget target, Instant deadline) {
        while (Instant.now().isBefore(deadline)) {
            try {
                GetCommandInvocationResponse invocation = ssm.getCommandInvocation(
                        GetCommandInvocationRequest.builder()
                                .commandId(commandId)
                                .instanceId(target.getId())
                                .build());

                String status = invocation.statusAsString();
                if (TERMINAL_STATUSES.contains(status)) {
                    String stdout = invocation.standardOutputContent() != null
                            ? invocation.standardOutputContent() : "";
                    boolean truncated = stdout.length() >= OUTPUT_LIMIT_BYTES;
                    if (truncated) {
                        log.info("Output from {} was truncated at the SSM limit; results may be incomplete",
                                target.getId());
                    }
                    return new ComputeCommandResult(target, "Success".equals(status), stdout,
                            invocation.standardErrorContent(), truncated);
                }
            } catch (InvocationDoesNotExistException notYet) {
                // SendCommand is eventually consistent; the invocation appears shortly.
            } catch (SdkException e) {
                return ComputeCommandResult.failure(target, "GetCommandInvocation failed: " + e.getMessage());
            }

            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return ComputeCommandResult.failure(target, "Interrupted while waiting for the command");
            }
        }
        return ComputeCommandResult.failure(target, "Timed out waiting for the SSM command to finish");
    }
}
