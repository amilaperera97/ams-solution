package uk.co.ams.certplatform.domain.enums;

/**
 * Whether a cloud service partitions its resources by region.
 *
 * <p>This drives how the planner expands a scan. A REGIONAL service is visited
 * once per requested region; a GLOBAL service (CloudFront, IAM, Azure Key Vault
 * certificates at tenant level) is visited exactly once per account, no matter
 * how many regions the scan asked for, using the descriptor's home region.
 * Without the distinction a five-region scan would list the same CloudFront
 * distributions five times and report five copies of every certificate.
 */
public enum ServiceScope {
    REGIONAL,
    GLOBAL
}
