package uk.co.ams.certplatform.infrastructure.discovery.aws;

import uk.co.ams.certplatform.application.port.CertificateDiscoveryStrategy;
import uk.co.ams.certplatform.application.port.DiscoveryCapability;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.DiscoveryResult;
import uk.co.ams.certplatform.domain.model.ScanContext;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.springframework.stereotype.Component;

@Component
public class AwsSecretsManagerCertificateDiscoveryStrategy implements CertificateDiscoveryStrategy {

    private final CloudProviderProperties properties;

    public AwsSecretsManagerCertificateDiscoveryStrategy(CloudProviderProperties properties) {
        this.properties = properties;
    }

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
        
        if (properties.isReal(CloudProviderType.AWS)) {
            result.setStatus("SKIPPED");
            result.setMessage("Secrets Manager discovery is not implemented for REAL mode; no secrets were read from AWS.");
            return result;
        }

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
