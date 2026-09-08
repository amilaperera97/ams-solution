package uk.co.ams.certplatform.infrastructure.discovery.aws;

import uk.co.ams.certplatform.application.port.CertificateDiscoveryStrategy;
import uk.co.ams.certplatform.application.port.DiscoveryCapability;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Certificate;
import uk.co.ams.certplatform.domain.model.CertificateUsage;
import uk.co.ams.certplatform.domain.model.DiscoveryResult;
import uk.co.ams.certplatform.domain.model.ScanContext;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class AwsAcmCertificateDiscoveryStrategy implements CertificateDiscoveryStrategy {

    @Override
    public DiscoveryCapability capability() {
        return new DiscoveryCapability(CloudProviderType.AWS, "ACM", "AwsAcmCertificateDiscovery");
    }

    @Override
    public boolean supports(ScanContext context) {
        return "ACM".equalsIgnoreCase(context.getService());
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
            // In a real implementation, we would use the AWS SDK (e.g. AcmClient) here.
            // For now, we simulate finding a certificate.
            
            Certificate mockCert = new Certificate();
            mockCert.setId("cert-" + UUID.randomUUID().toString());
            mockCert.setDomain("acm.example.com");
            mockCert.setProvider("AWS");
            mockCert.setAccountId(context.getAccount().getId());
            mockCert.setRegion(context.getRegion());
            mockCert.setService("ACM");
            mockCert.setSourceType("ACM_MANAGED");
            mockCert.setStatus("ISSUED");
            mockCert.setFingerprint("AA:BB:CC:DD:EE");
            mockCert.setIssuedDate(Instant.now().minusSeconds(86400 * 30)); // 30 days ago
            mockCert.setExpiryDate(Instant.now().plusSeconds(86400 * 365)); // 1 year from now
            
            CertificateUsage usage = new CertificateUsage();
            usage.setService("ACM");
            usage.setUsageType("MANAGED");
            usage.setRegion(context.getRegion());
            usage.setAccount(context.getAccount().getId());
            mockCert.addUsage(usage);

            result.addCertificate(mockCert);
            result.setStatus("SUCCESS");
        } catch (Exception e) {
            result.setStatus("FAILED");
            result.addError(e.getMessage());
        }
        
        return result;
    }
}
