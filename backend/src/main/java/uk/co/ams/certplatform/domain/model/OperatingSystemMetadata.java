package uk.co.ams.certplatform.domain.model;

/**
 * What the platform managed to learn about the OS of the machine a certificate
 * was found on. Every component is best-effort - a provider that reports nothing
 * yields {@link #unknown()} rather than a null reference.
 */
public record OperatingSystemMetadata(String family, String name, String version, String architecture) {

    private static final OperatingSystemMetadata UNKNOWN = new OperatingSystemMetadata(null, null, null, null);

    public static OperatingSystemMetadata unknown() {
        return UNKNOWN;
    }

    public static OperatingSystemMetadata of(String family, String name, String version) {
        return new OperatingSystemMetadata(family, name, version, null);
    }
}
