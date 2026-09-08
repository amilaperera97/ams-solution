package uk.co.ams.certplatform.application.port;

import uk.co.ams.certplatform.domain.model.DiscoveryResult;
import uk.co.ams.certplatform.domain.model.ScanContext;

public interface CertificateDiscoveryStrategy {

    DiscoveryCapability capability();

    boolean supports(ScanContext context);

    DiscoveryResult discover(ScanContext context);
}
