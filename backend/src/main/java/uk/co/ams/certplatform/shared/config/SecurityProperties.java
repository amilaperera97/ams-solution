package uk.co.ams.certplatform.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code certplatform.security.*}.
 */
@ConfigurationProperties(prefix = "certplatform.security")
public class SecurityProperties {

    /**
     * Key used to encrypt account secrets at rest. Supply base64-encoded 16/24/32
     * raw bytes, or any passphrase (hashed to a 256-bit key). Normally injected
     * from the CERTPLATFORM_SECRET_KEY environment variable - never commit it.
     */
    private String secretKey;

    /**
     * When true the application refuses to start without a secret key, so a
     * profile that holds real cloud credentials can never fall back to plaintext.
     */
    private boolean requireEncryption = false;

    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }

    public boolean isRequireEncryption() { return requireEncryption; }
    public void setRequireEncryption(boolean requireEncryption) { this.requireEncryption = requireEncryption; }
}
