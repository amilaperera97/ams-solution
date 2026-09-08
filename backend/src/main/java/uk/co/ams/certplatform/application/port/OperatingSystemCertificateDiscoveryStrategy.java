package uk.co.ams.certplatform.application.port;

import uk.co.ams.certplatform.domain.model.DiscoveryResult;
import uk.co.ams.certplatform.domain.model.ScanContext;
import uk.co.ams.certplatform.domain.model.OperatingSystemMetadata;

public interface OperatingSystemCertificateDiscoveryStrategy {

    boolean supports(OperatingSystemMetadata osMetadata);

    DiscoveryResult discover(ScanContext context, OperatingSystemMetadata osMetadata, String targetInstanceId);
}
