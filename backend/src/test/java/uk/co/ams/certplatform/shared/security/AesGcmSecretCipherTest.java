package uk.co.ams.certplatform.shared.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmSecretCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void shouldRoundTripASecret() {
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(KEY);

        String encrypted = cipher.encrypt("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY");

        assertTrue(encrypted.startsWith(SecretCipher.PREFIX));
        assertFalse(encrypted.contains("wJalrXUtnFEMI"));
        assertEquals("wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY", cipher.decrypt(encrypted));
    }

    @Test
    void shouldProduceDifferentCiphertextForTheSameInput() {
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(KEY);

        assertNotEquals(cipher.encrypt("same-secret"), cipher.encrypt("same-secret"),
                "a random IV per value must prevent identical ciphertexts");
    }

    @Test
    void shouldNotDoubleEncrypt() {
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(KEY);

        String once = cipher.encrypt("secret");
        assertEquals(once, cipher.encrypt(once));
    }

    @Test
    void shouldPassThroughPlaintextRowsWrittenBeforeEncryptionExisted() {
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(KEY);

        assertEquals("legacy-plaintext-token", cipher.decrypt("legacy-plaintext-token"));
    }

    @Test
    void shouldLeaveNullAndEmptyAlone() {
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(KEY);

        assertNull(cipher.encrypt(null));
        assertNull(cipher.decrypt(null));
        assertEquals("", cipher.encrypt(""));
    }

    @Test
    void shouldFailWhenDecryptingWithTheWrongKey() {
        String encrypted = new AesGcmSecretCipher(KEY).encrypt("secret");
        AesGcmSecretCipher other = new AesGcmSecretCipher("a-completely-different-passphrase");

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> other.decrypt(encrypted));
        assertTrue(e.getMessage().contains("CERTPLATFORM_SECRET_KEY"));
    }

    @Test
    void shouldAcceptEitherABase64KeyOrAPassphrase() {
        assertEquals(32, AesGcmSecretCipher.deriveKey(KEY).length);
        assertEquals(32, AesGcmSecretCipher.deriveKey("just a passphrase").length);
        assertThrows(IllegalArgumentException.class, () -> AesGcmSecretCipher.deriveKey("  "));
    }

    @Test
    void plaintextCipherShouldRefuseToReturnCiphertext() {
        PlaintextSecretCipher plaintext = new PlaintextSecretCipher();
        String encrypted = new AesGcmSecretCipher(KEY).encrypt("secret");

        assertFalse(plaintext.encryptionEnabled());
        assertEquals("still-plaintext", plaintext.decrypt("still-plaintext"));
        assertThrows(IllegalStateException.class, () -> plaintext.decrypt(encrypted));
    }

    @Test
    void maskerShouldRevealOnlyTheTail() {
        assertEquals("****MPLE", SecretMasker.mask("AKIAIOSFODNN7EXAMPLE"));
        assertEquals("****", SecretMasker.mask("abc"));
        assertNull(SecretMasker.mask(null));
        assertNull(SecretMasker.mask("   "));
    }
}
