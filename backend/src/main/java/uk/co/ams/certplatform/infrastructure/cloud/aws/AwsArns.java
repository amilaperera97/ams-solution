package uk.co.ams.certplatform.infrastructure.cloud.aws;

import java.util.Locale;

/** Small helpers for picking apart ARNs, shared by every AWS strategy. */
public final class AwsArns {

    private AwsArns() {}

    /** {@code arn:aws:acm:eu-west-2:123:certificate/abc} -> {@code acm} */
    public static String serviceOf(String arn) {
        if (arn == null) return "UNKNOWN";
        String[] parts = arn.split(":");
        return parts.length > 2 && !parts[2].isBlank() ? parts[2] : "UNKNOWN";
    }

    /** {@code arn:aws:acm:eu-west-2:123:certificate/abc} -> {@code eu-west-2} (empty for global services) */
    public static String regionOf(String arn) {
        if (arn == null) return null;
        String[] parts = arn.split(":");
        return parts.length > 3 && !parts[3].isBlank() ? parts[3] : null;
    }

    /** {@code arn:aws:acm:eu-west-2:123:certificate/abc} -> {@code 123} */
    public static String accountOf(String arn) {
        if (arn == null) return null;
        String[] parts = arn.split(":");
        return parts.length > 4 && !parts[4].isBlank() ? parts[4] : null;
    }

    /** Everything after the fifth colon: {@code certificate/abc} */
    public static String resourceOf(String arn) {
        if (arn == null) return null;
        String[] parts = arn.split(":", 6);
        return parts.length > 5 ? parts[5] : null;
    }

    /** Last path segment of the resource part: {@code app/my-lb/50dc6c495c0c9188} -> {@code 50dc6c495c0c9188} */
    public static String lastSegmentOf(String arn) {
        String resource = resourceOf(arn);
        if (resource == null) return null;
        int slash = resource.lastIndexOf('/');
        return slash >= 0 ? resource.substring(slash + 1) : resource;
    }

    public static boolean isAcmCertificate(String arn) {
        return arn != null && "acm".equalsIgnoreCase(serviceOf(arn));
    }

    public static boolean isIamServerCertificate(String arn) {
        return arn != null && "iam".equalsIgnoreCase(serviceOf(arn))
                && String.valueOf(resourceOf(arn)).toLowerCase(Locale.ROOT).startsWith("server-certificate/");
    }

    /** {@code arn:aws:iam::123:server-certificate/prod/my-cert} -> {@code my-cert} */
    public static String iamServerCertificateName(String arn) {
        return lastSegmentOf(arn);
    }
}
