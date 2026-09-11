package uk.co.ams.certplatform.domain.model;

/**
 * The application a certificate is serving, when discovery can work it out - the
 * runtime and framework behind a listener, or the process holding a key on disk.
 *
 * @param applicationName human-readable application the certificate belongs to
 * @param runtime         language runtime, e.g. "java"
 * @param runtimeVersion  runtime version, e.g. "21"
 * @param framework       application framework, e.g. "spring-boot"
 * @param frameworkVersion framework version
 * @param processName     OS process observed holding the certificate
 * @param deploymentType  how the application is deployed, e.g. "ECS", "SYSTEMD"
 */
public record ApplicationMetadata(
        String applicationName,
        String runtime,
        String runtimeVersion,
        String framework,
        String frameworkVersion,
        String processName,
        String deploymentType
) {
}
