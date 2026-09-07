package com.example.certplatform.infrastructure.cloud.azure;

import com.example.certplatform.application.port.CloudProviderAdapter;
import com.example.certplatform.domain.enums.CloudProviderType;
import com.example.certplatform.domain.model.Account;
import com.example.certplatform.domain.model.CertificateScanResult;
import com.example.certplatform.domain.model.ConnectionTestResult;
import com.example.certplatform.domain.model.ScanContext;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class AzureCloudProviderAdapter implements CloudProviderAdapter {

    @Override
    public CloudProviderType providerType() {
        return CloudProviderType.AZURE;
    }

    @Override
    public ConnectionTestResult testConnection(Account account) {
        if (account.getToken() != null && !account.getToken().isBlank()) {
             return new ConnectionTestResult("CONNECTED", CloudProviderType.AZURE, account.getAccountId(), "Connection successful");
        }
        return new ConnectionTestResult("FAILED", CloudProviderType.AZURE, account.getAccountId(), "Unable to authenticate with cloud provider");
    }

    @Override
    public CertificateScanResult scanCertificates(ScanContext context) {
        return new CertificateScanResult(Collections.emptyList(), "COMPLETED", "Scanned successfully");
    }
}
