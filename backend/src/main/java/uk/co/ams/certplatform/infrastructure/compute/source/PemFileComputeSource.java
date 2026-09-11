package uk.co.ams.certplatform.infrastructure.compute.source;

import uk.co.ams.certplatform.application.port.ComputeCertificateSource;
import uk.co.ams.certplatform.domain.enums.OsFamily;
import uk.co.ams.certplatform.domain.model.Certificate;
import uk.co.ams.certplatform.domain.model.ComputeCommand;
import uk.co.ams.certplatform.domain.model.ComputeCommandResult;
import uk.co.ams.certplatform.domain.model.ResourceTag;
import uk.co.ams.certplatform.domain.model.ScanContext;
import uk.co.ams.certplatform.infrastructure.discovery.support.X509CertificateParser;
import uk.co.ams.certplatform.shared.config.DiscoveryProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * PEM and DER certificate files on a Unix filesystem.
 *
 * <p>The searched directories are configurable and deliberately exclude the OS
 * trust store ({@code /etc/ssl/certs} and friends). That directory holds a few
 * hundred public CA certificates on every machine in the estate: scanning it
 * would bury the handful of certificates an operator actually has to renew, and
 * would blow past the Run Command output limit on the first instance.
 */
@Component
public class PemFileComputeSource implements ComputeCertificateSource {

    /** Keeps one pathological instance from filling the output buffer. */
    private static final int MAX_FILES_PER_HOST = 150;

    private final DiscoveryProperties properties;
    private final X509CertificateParser parser;

    public PemFileComputeSource(DiscoveryProperties properties, X509CertificateParser parser) {
        this.properties = properties;
        this.parser = parser;
    }

    @Override
    public String name() {
        return "FILESYSTEM_PEM";
    }

    @Override
    public boolean supports(OsFamily osFamily) {
        return osFamily == OsFamily.LINUX || osFamily == OsFamily.MACOS;
    }

    @Override
    public ComputeCommand command(ScanContext context, OsFamily osFamily) {
        String directories = String.join(" ", properties.getCompute().getLinuxPaths().stream()
                .map(PemFileComputeSource::shellQuote).toList());

        // Plain POSIX sh with awk: no jq, no python, nothing that a hardened image
        // might have stripped. awk extracts the first certificate block's base64
        // body, which is already the DER encoding the parser wants.
        List<String> script = List.of(
                "set -u",
                "found=0",
                "for dir in " + directories + "; do",
                "  [ -d \"$dir\" ] || continue",
                "  find \"$dir\" -maxdepth 4 -type f \\( -name '*.pem' -o -name '*.crt' "
                        + "-o -name '*.cer' -o -name '*.cert' \\) 2>/dev/null | while read -r file; do",
                "    body=$(awk '/-----BEGIN CERTIFICATE-----/{flag=1;next} "
                        + "/-----END CERTIFICATE-----/{if(flag)exit} flag{printf \"%s\", $0}' \"$file\" 2>/dev/null)",
                "    [ -n \"$body\" ] || continue",
                "    printf 'CERT|%s|%s\\n' \"$file\" \"$body\"",
                "  done",
                "done | head -n " + MAX_FILES_PER_HOST,
                "exit 0");

        return new ComputeCommand(osFamily, script,
                Duration.ofSeconds(properties.getCompute().getCommandTimeoutSeconds()));
    }

    @Override
    public List<Certificate> parse(ScanContext context, ComputeCommandResult result) {
        List<Certificate> certificates = new ArrayList<>();
        for (ComputeOutput.Record record : ComputeOutput.parse(result.stdout())) {
            parser.fromBase64Der(record.base64Der()).ifPresent(certificate -> certificates.add(
                    certificate.toBuilder()
                            .sourceType(name())
                            .resource(record.location())
                            .tag(new ResourceTag("file:path", record.location()))
                            .build()));
        }
        return certificates;
    }

    /** Single-quoted for sh, with embedded quotes escaped the POSIX way. */
    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
