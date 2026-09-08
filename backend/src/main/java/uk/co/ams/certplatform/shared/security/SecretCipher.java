package uk.co.ams.certplatform.shared.security;

/**
 * Encrypts account secrets (AWS secret access keys, bearer tokens) on the way
 * into the database and decrypts them on the way out.
 */
public interface SecretCipher {

    /** Marker on every stored ciphertext, so plaintext rows written before encryption existed still load. */
    String PREFIX = "enc:v1:";

    String encrypt(String plaintext);

    String decrypt(String stored);

    boolean encryptionEnabled();

    static boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
