package uk.co.ams.certplatform.infrastructure.discovery.aws.phase3;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.DiscoveryServiceDescriptor;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsClientFactory;
import uk.co.ams.certplatform.infrastructure.cloud.aws.UnimplementedAwsDiscoveryStrategy;
import uk.co.ams.certplatform.infrastructure.discovery.support.SimulatedCertificateFactory;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.springframework.stereotype.Component;

/**
 * Service 15: Java keystores and PKCS#12 on compute.
 *
 * <p>JKS, PKCS#12 and BCFKS files on instance filesystems - where most
 * Java estates actually keep their server certificates, invisible to
 * the PEM file scan.
 * 
 * The transport already exists: add a ComputeCertificateSource that
 * finds keystore files and runs keytool -list -rfc against them, then
 * point this strategy at ComputeCertificateScanner exactly as
 * AwsEc2FilesystemDiscoveryStrategy does. The interesting problem is
 * passwords - certificate-discovery.compute.keystore-passwords holds the
 * list to try, and a keystore that opens with none of them should be
 * reported as found-but-unreadable rather than skipped silently.
 *
 * <p><b>Not implemented yet.</b> Registered so the service appears in the
 * capabilities API and the scan picker as a known, greyed-out capability with
 * the permissions it will need. To build it: change the base class to
 * {@code AbstractAwsDiscoveryStrategy}, drop {@code notImplemented()} from the
 * descriptor, and write {@code discoverLive}.
 */
@Component
public class AwsKeystoreDiscoveryStrategy extends UnimplementedAwsDiscoveryStrategy {

    private static final DiscoveryServiceDescriptor DESCRIPTOR =
            DiscoveryServiceDescriptor.builder(CloudProviderType.AWS, "KEYSTORE", "Java keystores and PKCS#12 on compute")
                    .phase(3)
                    .aliases("JKS", "PKCS12", "P12", "PFX", "JAVA_KEYSTORE")
                    .requiredPermissions("ssm:DescribeInstanceInformation", "ssm:SendCommand", "ssm:GetCommandInvocation")
                    .notImplemented()
                    .build();

    public AwsKeystoreDiscoveryStrategy(CloudProviderProperties providerProperties,
                                            SimulatedCertificateFactory simulator,
                                            AwsClientFactory clientFactory) {
        super(providerProperties, simulator, clientFactory);
    }

    @Override
    public DiscoveryServiceDescriptor descriptor() {
        return DESCRIPTOR;
    }
}
