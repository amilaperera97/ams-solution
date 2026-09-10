package uk.co.ams.certplatform.infrastructure.discovery.aws.phase2;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.DiscoveryServiceDescriptor;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsClientFactory;
import uk.co.ams.certplatform.infrastructure.cloud.aws.UnimplementedAwsDiscoveryStrategy;
import uk.co.ams.certplatform.infrastructure.discovery.support.SimulatedCertificateFactory;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.springframework.stereotype.Component;

/**
 * Service 9: Secrets Manager.
 *
 * <p>Certificates are routinely parked in Secrets Manager, usually as a
 * PEM bundle or a base64 PKCS#12.
 * 
 * The listing is safe and should always run - name, tags, rotation
 * state and last-changed date are enough to flag a certificate-shaped
 * secret. Retrieving values is not: it decrypts every secret in the
 * account and writes an audit trail that says so. Gate it on
 * DiscoveryOptions.readSecretContent, and prefer to fetch only secrets
 * whose name, tag or description suggests a certificate.
 *
 * <p><b>Not implemented yet.</b> Registered so the service appears in the
 * capabilities API and the scan picker as a known, greyed-out capability with
 * the permissions it will need. To build it: change the base class to
 * {@code AbstractAwsDiscoveryStrategy}, drop {@code notImplemented()} from the
 * descriptor, and write {@code discoverLive}.
 */
@Component
public class AwsSecretsManagerDiscoveryStrategy extends UnimplementedAwsDiscoveryStrategy {

    private static final DiscoveryServiceDescriptor DESCRIPTOR =
            DiscoveryServiceDescriptor.builder(CloudProviderType.AWS, "SECRETS_MANAGER", "Secrets Manager")
                    .phase(2)
                    .aliases("SECRETSMANAGER", "SECRETS", "ASM")
                    .requiredPermissions("secretsmanager:ListSecrets", "secretsmanager:GetSecretValue", "secretsmanager:ListSecretVersionIds")
                    .notImplemented()
                    .build();

    public AwsSecretsManagerDiscoveryStrategy(CloudProviderProperties providerProperties,
                                                  SimulatedCertificateFactory simulator,
                                                  AwsClientFactory clientFactory) {
        super(providerProperties, simulator, clientFactory);
    }

    @Override
    public DiscoveryServiceDescriptor descriptor() {
        return DESCRIPTOR;
    }
}
