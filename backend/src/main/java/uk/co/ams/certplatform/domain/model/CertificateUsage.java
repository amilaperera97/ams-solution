package uk.co.ams.certplatform.domain.model;

/**
 * One place a certificate is actually in use. A certificate found in ACM and
 * bound to two load balancers has one {@link Certificate} and two usages, which
 * is what lets the UI answer "what breaks when this expires?".
 *
 * @param service      catalogue key of the service holding the certificate, e.g. "ALB"
 * @param resource     provider identifier of the holder, normally an ARN
 * @param resourceType human-readable kind of holder, e.g. "EC2 instance"
 * @param usageType    how it is used, e.g. "LISTENER", "ON_DISK"
 * @param region       region the holder lives in
 * @param account      account the holder lives in
 * @param environment  environment the holder was attributed to
 * @param application  the application behind the holder, when known
 * @param os           the OS of the machine, for on-disk usages
 */
public record CertificateUsage(
        String service,
        String resource,
        String resourceType,
        String usageType,
        String region,
        String account,
        String environment,
        ApplicationMetadata application,
        OperatingSystemMetadata os
) {

    /** A global service records its usage in its home region, not the caller's. */
    public CertificateUsage withRegion(String region) {
        return new CertificateUsage(service, resource, resourceType, usageType, region,
                account, environment, application, os);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String service;
        private String resource;
        private String resourceType;
        private String usageType;
        private String region;
        private String account;
        private String environment;
        private ApplicationMetadata application;
        private OperatingSystemMetadata os;

        private Builder() {}

        public Builder service(String service) { this.service = service; return this; }
        public Builder resource(String resource) { this.resource = resource; return this; }
        public Builder resourceType(String resourceType) { this.resourceType = resourceType; return this; }
        public Builder usageType(String usageType) { this.usageType = usageType; return this; }
        public Builder region(String region) { this.region = region; return this; }
        public Builder account(String account) { this.account = account; return this; }
        public Builder environment(String environment) { this.environment = environment; return this; }
        public Builder application(ApplicationMetadata application) { this.application = application; return this; }
        public Builder os(OperatingSystemMetadata os) { this.os = os; return this; }

        public CertificateUsage build() {
            return new CertificateUsage(service, resource, resourceType, usageType, region,
                    account, environment, application, os);
        }
    }
}
