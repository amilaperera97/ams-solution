package uk.co.ams.certplatform.infrastructure.compute;

import uk.co.ams.certplatform.application.port.ComputeCertificateSource;
import uk.co.ams.certplatform.application.port.ComputeExecutor;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.enums.DiscoveryStatus;
import uk.co.ams.certplatform.domain.enums.OsFamily;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.domain.model.Certificate;
import uk.co.ams.certplatform.domain.model.ComputeCommand;
import uk.co.ams.certplatform.domain.model.ComputeCommandResult;
import uk.co.ams.certplatform.domain.model.ComputeTarget;
import uk.co.ams.certplatform.domain.model.DiscoveryOptions;
import uk.co.ams.certplatform.domain.model.DiscoveryResult;
import uk.co.ams.certplatform.domain.model.ScanContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComputeCertificateScannerTest {

    private static final Account ACCOUNT = account();

    @Test
    void stampsProvenanceAndAnOnDiskUsageOntoEveryCertificateFound() {
        ComputeTarget instance = target("i-123", OsFamily.LINUX);
        RecordingExecutor executor = new RecordingExecutor(List.of(instance), target ->
                new ComputeCommandResult(target, true, "one", "", false));

        DiscoveryResult result = scan(new ComputeCertificateScanner(
                List.of(executor), List.of(new StubSource(OsFamily.LINUX, 1))));

        assertEquals(DiscoveryStatus.SUCCESS, result.getStatus());
        assertEquals(1, result.getCertificates().size());

        Certificate certificate = result.getCertificates().get(0);
        assertEquals("AWS", certificate.getProvider());
        assertEquals("acc-1", certificate.getAccountId());
        assertEquals("eu-west-2", certificate.getRegion());
        assertEquals("EC2", certificate.getService());
        assertEquals(Boolean.FALSE, certificate.getAutoRenewal(),
                "nothing on a filesystem renews itself - that is the point of finding it");
        assertEquals("ON_DISK", certificate.getUsages().get(0).getUsageType());
        assertEquals("i-123", certificate.getUsages().get(0).getResource());
        assertTrue(certificate.getTags().stream()
                .anyMatch(tag -> "compute:instanceId".equals(tag.getKey()) && "i-123".equals(tag.getValue())));
    }

    @Test
    void sendsEachPlatformOnlyTheSourcesThatSupportIt() {
        RecordingExecutor executor = new RecordingExecutor(
                List.of(target("i-linux", OsFamily.LINUX), target("i-win", OsFamily.WINDOWS)),
                target -> new ComputeCommandResult(target, true, "one", "", false));

        StubSource unix = new StubSource(OsFamily.LINUX, 1);
        StubSource windows = new StubSource(OsFamily.WINDOWS, 1);
        scan(new ComputeCertificateScanner(List.of(executor), List.of(unix, windows)));

        assertEquals(List.of("i-linux"), unix.scannedInstanceIds);
        assertEquals(List.of("i-win"), windows.scannedInstanceIds);
    }

    @Test
    void treatsAnInstanceThatCouldNotBeReachedAsAGapNotAFailure() {
        RecordingExecutor executor = new RecordingExecutor(
                List.of(target("i-ok", OsFamily.LINUX), target("i-broken", OsFamily.LINUX)),
                target -> "i-broken".equals(target.getId())
                        ? ComputeCommandResult.failure(target, "agent not responding")
                        : new ComputeCommandResult(target, true, "one", "", false));

        DiscoveryResult result = scan(new ComputeCertificateScanner(
                List.of(executor), List.of(new StubSource(OsFamily.LINUX, 1))));

        assertEquals(DiscoveryStatus.PARTIAL, result.getStatus(),
                "one unreachable box means an incomplete answer, not a failed one");
        assertEquals(1, result.getCertificates().size());
        assertTrue(result.getErrors().get(0).contains("i-broken"));
    }

    @Test
    void flagsTruncatedOutputSoAPartialReadIsNotMistakenForACleanOne() {
        RecordingExecutor executor = new RecordingExecutor(List.of(target("i-busy", OsFamily.LINUX)),
                target -> new ComputeCommandResult(target, true, "one", "", true));

        DiscoveryResult result = scan(new ComputeCertificateScanner(
                List.of(executor), List.of(new StubSource(OsFamily.LINUX, 1))));

        assertEquals(DiscoveryStatus.PARTIAL, result.getStatus());
        assertTrue(result.getErrors().get(0).contains("truncated"));
    }

    @Test
    void reportsSuccessWhenNothingWasReachableBecauseNothingWasMissed() {
        DiscoveryResult result = scan(new ComputeCertificateScanner(
                List.of(new RecordingExecutor(List.of(), target -> null)),
                List.of(new StubSource(OsFamily.LINUX, 1))));

        assertEquals(DiscoveryStatus.SUCCESS, result.getStatus());
        assertTrue(result.getMessage().contains("No agent-managed instances"));
    }

    @Test
    void scansAnUnidentifiedPlatformAsUnix() {
        RecordingExecutor executor = new RecordingExecutor(List.of(target("i-mystery", OsFamily.UNKNOWN)),
                target -> new ComputeCommandResult(target, true, "one", "", false));
        StubSource unix = new StubSource(OsFamily.LINUX, 1);

        scan(new ComputeCertificateScanner(List.of(executor), List.of(unix)));

        assertEquals(List.of("i-mystery"), unix.scannedInstanceIds);
    }

    @Test
    void skipsEntirelyWhenFilesystemScanningIsTurnedOff() {
        DiscoveryResult result = scan(new ComputeCertificateScanner(
                        List.of(new RecordingExecutor(List.of(target("i-1", OsFamily.LINUX)), target -> null)),
                        List.of(new StubSource(OsFamily.LINUX, 1))),
                new DiscoveryOptions(false, false, 5000));

        assertEquals(DiscoveryStatus.SKIPPED, result.getStatus());
    }

    @Test
    void saysSoWhenNoExecutorCanReachTheProvider() {
        DiscoveryResult result = scan(new ComputeCertificateScanner(List.of(), List.of()));

        assertEquals(DiscoveryStatus.NOT_IMPLEMENTED, result.getStatus());
    }

    // --- harness -------------------------------------------------------------

    private DiscoveryResult scan(ComputeCertificateScanner scanner) {
        return scan(scanner, DiscoveryOptions.defaults());
    }

    private DiscoveryResult scan(ComputeCertificateScanner scanner, DiscoveryOptions options) {
        ScanContext context = new ScanContext("scan-1", ACCOUNT, CloudProviderType.AWS,
                "eu-west-2", "EC2", null, options);
        DiscoveryResult result = new DiscoveryResult("AWS", "acc-1", "eu-west-2", "EC2");
        scanner.scan(context, result, "EC2", "EC2 instance");
        return result;
    }

    private static Account account() {
        Account account = new Account();
        account.setId("acc-1");
        account.setRegion("eu-west-2");
        return account;
    }

    private static ComputeTarget target(String id, OsFamily osFamily) {
        ComputeTarget target = new ComputeTarget(id, osFamily);
        target.setService("EC2");
        return target;
    }

    /** Answers with canned results and records which targets it was handed. */
    private static final class RecordingExecutor implements ComputeExecutor {
        private final List<ComputeTarget> targets;
        private final java.util.function.Function<ComputeTarget, ComputeCommandResult> responder;

        RecordingExecutor(List<ComputeTarget> targets,
                          java.util.function.Function<ComputeTarget, ComputeCommandResult> responder) {
            this.targets = targets;
            this.responder = responder;
        }

        @Override public boolean supports(CloudProviderType provider) { return provider == CloudProviderType.AWS; }
        @Override public List<ComputeTarget> listTargets(ScanContext context) { return targets; }

        @Override
        public List<ComputeCommandResult> run(ScanContext context, List<ComputeTarget> batch, ComputeCommand command) {
            return batch.stream().map(responder).toList();
        }
    }

    /** Produces a fixed number of bare certificates per successful host. */
    private static final class StubSource implements ComputeCertificateSource {
        private final OsFamily supported;
        private final int certificatesPerHost;
        final List<String> scannedInstanceIds = new ArrayList<>();

        StubSource(OsFamily supported, int certificatesPerHost) {
            this.supported = supported;
            this.certificatesPerHost = certificatesPerHost;
        }

        @Override public String name() { return "STUB_" + supported; }
        @Override public boolean supports(OsFamily osFamily) { return osFamily == supported; }

        @Override
        public ComputeCommand command(ScanContext context, OsFamily osFamily) {
            return ComputeCommand.of(osFamily, List.of("echo hello"));
        }

        @Override
        public List<Certificate> parse(ScanContext context, ComputeCommandResult result) {
            scannedInstanceIds.add(result.target().getId());
            List<Certificate> certificates = new ArrayList<>();
            for (int i = 0; i < certificatesPerHost; i++) {
                Certificate certificate = new Certificate();
                certificate.setFingerprint("AA:" + result.target().getId() + ":" + i);
                certificates.add(certificate);
            }
            return certificates;
        }
    }
}
