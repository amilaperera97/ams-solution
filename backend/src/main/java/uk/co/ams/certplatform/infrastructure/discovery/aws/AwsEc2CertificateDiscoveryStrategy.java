package uk.co.ams.certplatform.infrastructure.discovery.aws;

import uk.co.ams.certplatform.application.port.CertificateDiscoveryStrategy;
import uk.co.ams.certplatform.application.port.DiscoveryCapability;
import uk.co.ams.certplatform.application.port.OperatingSystemCertificateDiscoveryStrategy;
import uk.co.ams.certplatform.application.port.RuntimeCertificateDiscoveryAdapter;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AwsEc2CertificateDiscoveryStrategy implements CertificateDiscoveryStrategy {

    private final List<OperatingSystemCertificateDiscoveryStrategy> osStrategies;
    private final List<RuntimeCertificateDiscoveryAdapter> runtimeAdapters;

    public AwsEc2CertificateDiscoveryStrategy(List<OperatingSystemCertificateDiscoveryStrategy> osStrategies,
                                              List<RuntimeCertificateDiscoveryAdapter> runtimeAdapters) {
        this.osStrategies = osStrategies;
        this.runtimeAdapters = runtimeAdapters;
    }

    @Override
    public DiscoveryCapability capability() {
        return new DiscoveryCapability(CloudProviderType.AWS, "EC2", "AwsEc2DeepDiscovery");
    }

    @Override
    public boolean supports(ScanContext context) {
        return "EC2".equalsIgnoreCase(context.getService());
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
            // Simulated EC2 discovery
            // 1. List EC2 instances
            // 2. Describe instance to get tags and platform details
            // 3. Infer OS and runtime
            
            // Dummy logic to represent the pipeline
            OperatingSystemMetadata osMeta = new OperatingSystemMetadata();
            osMeta.setFamily("LINUX");
            osMeta.setName("Ubuntu");
            osMeta.setVersion("24.04");
            
            ApplicationMetadata appMeta = new ApplicationMetadata();
            appMeta.setRuntime("JAVA");
            appMeta.setFramework("Spring Boot");
            
            String instanceId = "i-1234567890abcdef0";

            // Delegate to OS specific strategies
            for (OperatingSystemCertificateDiscoveryStrategy osStrategy : osStrategies) {
                if (osStrategy.supports(osMeta)) {
                    DiscoveryResult osResult = osStrategy.discover(context, osMeta, instanceId);
                    result.addCertificates(osResult.getCertificates());
                }
            }
            
            // Delegate to runtime specific strategies
            for (RuntimeCertificateDiscoveryAdapter rtAdapter : runtimeAdapters) {
                if (rtAdapter.supports(appMeta)) {
                    DiscoveryResult rtResult = rtAdapter.discover(context, appMeta, instanceId);
                    result.addCertificates(rtResult.getCertificates());
                }
            }

            result.setStatus("SUCCESS");
        } catch (Exception e) {
            result.setStatus("FAILED");
            result.addError(e.getMessage());
        }

        return result;
    }
}
