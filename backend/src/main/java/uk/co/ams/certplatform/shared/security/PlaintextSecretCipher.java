package uk.co.ams.certplatform.shared.security;

/**
 * Fallback for profiles with no secret key configured (dev). Values pass
 * through untouched, but an already-encrypted value fails loudly rather than
 * being handed to the AWS SDK as garbage.
 */
public class PlaintextSecretCipher implements SecretCipher {

    @Override
    public String encrypt(String plaintext) {
        return plaintext;
    }

    @Override
    public String decrypt(String stored) {
        if (SecretCipher.isEncrypted(stored)) {
            throw new IllegalStateException(
                "Stored secret is encrypted but no secret key is configured. "
                + "Set CERTPLATFORM_SECRET_KEY to the key this data was written with.");
        }
        return stored;
    }

    @Override
    public boolean encryptionEnabled() {
        return false;
    }
}
