package uk.co.ams.certplatform.infrastructure.discovery.aws.phase2;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.DiscoveryServiceDescriptor;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsClientFactory;
import uk.co.ams.certplatform.infrastructure.cloud.aws.UnimplementedAwsDiscoveryStrategy;
import uk.co.ams.certplatform.infrastructure.discovery.support.SimulatedCertificateFactory;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.springframework.stereotype.Component;

/**
 * Service 10: S3 objects.
 *
 * <p>Certificate files uploaded to a bucket and forgotten. The listing is
 * the whole difficulty: an estate can hold billions of objects, so a
 * full crawl is not an option. The workable approach is to filter by
 * extension (.pem, .crt, .cer, .p12, .pfx, .jks) and size during the
 * list, then fetch only what matches, with a per-bucket object cap.
 * 
 * Buckets are regional; ListAllMyBuckets is not. Whoever builds this
 * should list once and filter by GetBucketLocation rather than
 * re-listing in every region, or the same bucket gets scanned once per
 * region in the scan.
 *
 * <p><b>Not implemented yet.</b> Registered so the service appears in the
 * capabilities API and the scan picker as a known, greyed-out capability with
 * the permissions it will need. To build it: change the base class to
 * {@code AbstractAwsDiscoveryStrategy}, drop {@code notImplemented()} from the
 * descriptor, and write {@code discoverLive}.
 */
@Component
public class AwsS3DiscoveryStrategy extends UnimplementedAwsDiscoveryStrategy {

    private static final DiscoveryServiceDescriptor DESCRIPTOR =
            DiscoveryServiceDescriptor.builder(CloudProviderType.AWS, "S3", "S3 objects")
                    .phase(2)
                    .aliases("S3_BUCKETS", "OBJECT_STORAGE")
                    .requiredPermissions("s3:ListAllMyBuckets", "s3:GetBucketLocation", "s3:ListBucket", "s3:GetObject")
                    .notImplemented()
                    .build();

    public AwsS3DiscoveryStrategy(CloudProviderProperties providerProperties,
                                      SimulatedCertificateFactory simulator,
                                      AwsClientFactory clientFactory) {
        super(providerProperties, simulator, clientFactory);
    }

    @Override
    public DiscoveryServiceDescriptor descriptor() {
        return DESCRIPTOR;
    }
}
