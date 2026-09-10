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
 * The Windows certificate store.
 *
 * <p>On Windows a server certificate is almost never a file - IIS, RDP and
 * .NET services all read from {@code LocalMachine}. Scanning the disk for
 * {@code .pfx} files would find installers, not what is actually serving
 * traffic, and would not be readable without the export password anyway.
 */
@Component
public class WindowsCertificateStoreComputeSource implements ComputeCertificateSource {

    private static final int MAX_CERTIFICATES_PER_HOST = 150;

    private final DiscoveryProperties properties;
    private final X509CertificateParser parser;

    public WindowsCertificateStoreComputeSource(DiscoveryProperties properties, X509CertificateParser parser) {
        this.properties = properties;
        this.parser = parser;
    }

    @Override
    public String name() {
        return "WINDOWS_CERT_STORE";
    }

    @Override
    public boolean supports(OsFamily osFamily) {
        return osFamily == OsFamily.WINDOWS;
    }

    @Override
    public ComputeCommand command(ScanContext context, OsFamily osFamily) {
        String stores = String.join(",", properties.getCompute().getWindowsStores().stream()
                .map(WindowsCertificateStoreComputeSource::powerShellQuote).toList());

        List<String> script = List.of(
                "$ErrorActionPreference = 'SilentlyContinue'",
                "$stores = @(" + stores + ")",
                "$count = 0",
                "foreach ($store in $stores) {",
                "  if (-not (Test-Path $store)) { continue }",
                "  foreach ($cert in Get-ChildItem -Path $store) {",
                "    if ($count -ge " + MAX_CERTIFICATES_PER_HOST + ") { break }",
                "    $body = [Convert]::ToBase64String($cert.RawData)",
                "    Write-Output ('CERT|' + $store + '\\' + $cert.Thumbprint + '|' + $body)",
                "    $count++",
                "  }",
                "}",
                "exit 0");

        return new ComputeCommand(osFamily, script,
                Duration.ofSeconds(properties.getCompute().getCommandTimeoutSeconds()));
    }

    @Override
    public List<Certificate> parse(ScanContext context, ComputeCommandResult result) {
        List<Certificate> certificates = new ArrayList<>();
        for (ComputeOutput.Record record : ComputeOutput.parse(result.stdout())) {
            parser.fromBase64Der(record.base64Der()).ifPresent(certificate -> {
                certificate.setSourceType(name());
                certificate.setResource(record.location());
                certificate.addTag(new ResourceTag("windows:store", record.location()));
                certificates.add(certificate);
            });
        }
        return certificates;
    }

    private static String powerShellQuote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
