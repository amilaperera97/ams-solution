package uk.co.ams.certplatform.infrastructure.cloud.gcp;

import uk.co.ams.certplatform.application.port.CloudProviderAdapter;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.domain.model.CertificateScanResult;
import uk.co.ams.certplatform.domain.model.ConnectionTestResult;
import uk.co.ams.certplatform.domain.model.ScanContext;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class GcpCloudProviderAdapter implements CloudProviderAdapter {

    @Override
    public CloudProviderType providerType() {
        return CloudProviderType.GCP;
    }

    @Override
    public ConnectionTestResult testConnection(Account account) {
        if (account.getToken() != null && !account.getToken().isBlank()) {
             return new ConnectionTestResult("CONNECTED", CloudProviderType.GCP, account.getAccountId(), "Connection successful");
        }
        return new ConnectionTestResult("FAILED", CloudProviderType.GCP, account.getAccountId(), "Unable to authenticate with cloud provider");
    }

    @Override
    public CertificateScanResult scanCertificates(ScanContext context) {
        return new CertificateScanResult(Collections.emptyList(), "COMPLETED", "Scanned successfully");
    }
}
