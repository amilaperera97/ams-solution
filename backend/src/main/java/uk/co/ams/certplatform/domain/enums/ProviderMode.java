package uk.co.ams.certplatform.domain.enums;

/**
 * Whether a cloud provider integration talks to the real cloud API or to the
 * built-in simulation. The dev profile runs everything in MOCK; the qa profile
 * runs AWS in REAL so genuine credentials can be configured and exercised.
 */
public enum ProviderMode {
    MOCK,
    REAL
}
