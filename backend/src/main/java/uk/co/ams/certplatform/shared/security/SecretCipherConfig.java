package uk.co.ams.certplatform.shared.security;

import uk.co.ams.certplatform.shared.config.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecretCipherConfig {

    private static final Logger log = LoggerFactory.getLogger(SecretCipherConfig.class);

    @Bean
    public SecretCipher secretCipher(SecurityProperties properties) {
        String key = properties.getSecretKey();
        if (key != null && !key.isBlank()) {
            log.info("Account secrets will be encrypted at rest (AES-256-GCM).");
            return new AesGcmSecretCipher(key);
        }
        if (properties.isRequireEncryption()) {
            throw new IllegalStateException("""
                No secret key configured, but this profile requires encryption of stored credentials.
                Set the CERTPLATFORM_SECRET_KEY environment variable before starting, e.g.:
                  export CERTPLATFORM_SECRET_KEY="$(openssl rand -base64 32)"
                Keep the value safe - stored credentials cannot be read back without it.""");
        }
        log.warn("No CERTPLATFORM_SECRET_KEY set - account secrets will be stored in plaintext. "
                 + "Acceptable for mock data only; never point this profile at a real cloud account.");
        return new PlaintextSecretCipher();
    }
}
