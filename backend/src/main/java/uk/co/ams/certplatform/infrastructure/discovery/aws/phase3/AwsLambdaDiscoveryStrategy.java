package uk.co.ams.certplatform.infrastructure.discovery.aws.phase3;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.DiscoveryServiceDescriptor;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsClientFactory;
import uk.co.ams.certplatform.infrastructure.cloud.aws.UnimplementedAwsDiscoveryStrategy;
import uk.co.ams.certplatform.infrastructure.discovery.support.SimulatedCertificateFactory;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.springframework.stereotype.Component;

/**
 * Service 12: Lambda.
 *
 * <p>Functions that carry a client certificate for mutual TLS to a
 * backend, either bundled in the deployment package or in a layer.
 * Finding them means downloading and unpacking the artifact, so this
 * should be opt-in and capped by package size.
 * 
 * Environment variables holding a PEM are the cheaper and more common
 * case, and can be checked from GetFunctionConfiguration alone.
 *
 * <p><b>Not implemented yet.</b> Registered so the service appears in the
 * capabilities API and the scan picker as a known, greyed-out capability with
 * the permissions it will need. To build it: change the base class to
 * {@code AbstractAwsDiscoveryStrategy}, drop {@code notImplemented()} from the
 * descriptor, and write {@code discoverLive}.
 */
@Component
public class AwsLambdaDiscoveryStrategy extends UnimplementedAwsDiscoveryStrategy {

    private static final DiscoveryServiceDescriptor DESCRIPTOR =
            DiscoveryServiceDescriptor.builder(CloudProviderType.AWS, "LAMBDA", "Lambda")
                    .phase(3)
                    .aliases("FUNCTIONS", "LAMBDA_FUNCTIONS")
                    .requiredPermissions("lambda:ListFunctions", "lambda:GetFunction", "lambda:ListLayers")
                    .notImplemented()
                    .build();

    public AwsLambdaDiscoveryStrategy(CloudProviderProperties providerProperties,
                                          SimulatedCertificateFactory simulator,
                                          AwsClientFactory clientFactory) {
        super(providerProperties, simulator, clientFactory);
    }

    @Override
    public DiscoveryServiceDescriptor descriptor() {
        return DESCRIPTOR;
    }
}
