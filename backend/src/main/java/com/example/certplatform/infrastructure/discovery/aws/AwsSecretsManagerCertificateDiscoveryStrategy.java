package com.example.certplatform.infrastructure.discovery.aws;

import com.example.certplatform.application.port.CertificateDiscoveryStrategy;
import com.example.certplatform.application.port.DiscoveryCapability;
import com.example.certplatform.domain.enums.CloudProviderType;
import com.example.certplatform.domain.model.DiscoveryResult;
import com.example.certplatform.domain.model.ScanContext;
import org.springframework.stereotype.Component;

@Component
public class AwsSecretsManagerCertificateDiscoveryStrategy implements CertificateDiscoveryStrategy {

    @Override
    public DiscoveryCapability capability() {
        return new DiscoveryCapability(CloudProviderType.AWS, "SECRETS_MANAGER", "AwsSecretsManagerDiscovery");
    }

    @Override
    public boolean supports(ScanContext context) {
        return "SECRETS_MANAGER".equalsIgnoreCase(context.getService());
    }

    @Override
    public DiscoveryResult discover(ScanContext context) {
        DiscoveryResult result = new DiscoveryResult(
                CloudProviderType.AWS.name(),
                context.getAccount().getId(),
                context.getRegion(),
                context.getService()
        );
        
        try {
            // Simulated secure secret scanning
            // IMPORTANT: "Do not simply download every secret. Only retrieve secret content when explicitly allowed."
            result.setStatus("SUCCESS");
        } catch (Exception e) {
            result.setStatus("FAILED");
            result.addError(e.getMessage());
        }
        
        return result;
    }
}
