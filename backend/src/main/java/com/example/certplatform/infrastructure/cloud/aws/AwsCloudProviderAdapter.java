package com.example.certplatform.infrastructure.cloud.aws;

import com.example.certplatform.application.port.CloudProviderAdapter;
import com.example.certplatform.domain.enums.CloudProviderType;
import com.example.certplatform.domain.model.Account;
import com.example.certplatform.domain.model.CertificateScanResult;
import com.example.certplatform.domain.model.ConnectionTestResult;
import com.example.certplatform.domain.model.ScanContext;
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
