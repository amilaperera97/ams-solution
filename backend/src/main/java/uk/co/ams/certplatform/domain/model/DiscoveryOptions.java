package uk.co.ams.certplatform.domain.model;

/**
 * Per-scan switches that change how invasive discovery is allowed to be.
 *
 * <p>Kept separate from {@link ScanContext} identity so a strategy can ask
 * "am I allowed to do this?" without the caller having to thread extra
 * parameters through every signature. The defaults are the cautious ones: read
 * metadata freely, never pull secret material unless someone asked for it.
 *
 * @param readSecretContent   allow retrieving the body of a secret / S3 object / keystore.
 *                            Off by default - listing a Secrets Manager secret is cheap and safe,
 *                            downloading every secret value is neither.
 * @param scanComputeFilesystems allow running commands on instances to read certificates off disk
 * @param maxResourcesPerService safety valve against an account with tens of thousands of resources
 */
public record DiscoveryOptions(
        boolean readSecretContent,
        boolean scanComputeFilesystems,
        int maxResourcesPerService
) {

    public static DiscoveryOptions defaults() {
        return new DiscoveryOptions(false, true, 5000);
    }
}
