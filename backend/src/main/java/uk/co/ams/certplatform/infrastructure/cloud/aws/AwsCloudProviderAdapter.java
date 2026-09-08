package uk.co.ams.certplatform.infrastructure.cloud.aws;

import uk.co.ams.certplatform.application.port.CloudProviderAdapter;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.domain.model.CertificateScanResult;
import uk.co.ams.certplatform.domain.model.ConnectionTestResult;
import uk.co.ams.certplatform.domain.model.ScanContext;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class AwsCloudProviderAdapter implements CloudProviderAdapter {

    @Override
    public CloudProviderType providerType() {
        return CloudProviderType.AWS;
    }

    @Override
    public ConnectionTestResult testConnection(Account account) {
        // In a real implementation, this would use AWS SDK (or a RestTemplate for Mock) 
        // to call AWS STS GetCallerIdentity to verify credentials.
        // For now, we simulate success for demonstration purposes.
        if (account.getAccountId() != null && account.getAccountId().length() == 12) {
             return new ConnectionTestResult("CONNECTED", CloudProviderType.AWS, account.getAccountId(), "Connection successful");
        }
        return new ConnectionTestResult("FAILED", CloudProviderType.AWS, account.getAccountId(), "Unable to authenticate with cloud provider");
    }

    @Override
    public CertificateScanResult scanCertificates(ScanContext context) {
        return new CertificateScanResult(Collections.emptyList(), "COMPLETED", "Scanned successfully");
    }
}
