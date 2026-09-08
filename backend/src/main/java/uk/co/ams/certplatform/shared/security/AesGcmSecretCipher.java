package uk.co.ams.certplatform.shared.security;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM with a random 96-bit IV per value. Stored form is
 * {@code enc:v1:base64(iv || ciphertext || tag)}.
 */
public class AesGcmSecretCipher implements SecretCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmSecretCipher(String configuredKey) {
        this.key = new SecretKeySpec(deriveKey(configuredKey), "AES");
    }

    /**
     * Accepts a base64-encoded 16/24/32-byte key as-is; anything else is hashed
     * with SHA-256 so a human-typed passphrase still yields a valid AES key.
     */
    static byte[] deriveKey(String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new IllegalArgumentException("Secret key must not be blank");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(configuredKey.trim());
            if (decoded.length == 16 || decoded.length == 24 || decoded.length == 32) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // Not base64 - fall through to hashing the passphrase.
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(configuredKey.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return plaintext;
        if (SecretCipher.isEncrypted(plaintext)) return plaintext; // already encrypted - do not double-wrap
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt secret", e);
        }
    }

    @Override
    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) return stored;
        if (!SecretCipher.isEncrypted(stored)) return stored; // legacy plaintext row
        try {
            byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to decrypt a stored secret. This usually means CERTPLATFORM_SECRET_KEY "
                + "differs from the key the value was written with.", e);
        }
    }

    @Override
    public boolean encryptionEnabled() {
        return true;
    }
}
