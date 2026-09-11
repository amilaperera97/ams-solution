package uk.co.ams.certplatform.infrastructure.discovery.support;

import uk.co.ams.certplatform.domain.model.Certificate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The parser is the common identity path: a certificate found on an EC2 disk and
 * the same certificate described through ACM must produce the same fingerprint,
 * or the deduplicator reports one certificate as two.
 */
class X509CertificateParserTest {

    /** Self-signed, CN=discovery-test.example.com, SAN alt.example.com, valid to 2036. */
    private static final String PEM = """
            -----BEGIN CERTIFICATE-----
            MIIDqzCCApOgAwIBAgIUZkuiS9eJpElPXJ8PUvXJ0off4uAwDQYJKoZIhvcNAQEL
            BQAwSDEjMCEGA1UEAwwaZGlzY292ZXJ5LXRlc3QuZXhhbXBsZS5jb20xFDASBgNV
            BAoMC0V4YW1wbGUgTHRkMQswCQYDVQQGEwJHQjAeFw0yNjA5MTAwNzAxMzFaFw0z
            NjA5MDcwNzAxMzFaMEgxIzAhBgNVBAMMGmRpc2NvdmVyeS10ZXN0LmV4YW1wbGUu
            Y29tMRQwEgYDVQQKDAtFeGFtcGxlIEx0ZDELMAkGA1UEBhMCR0IwggEiMA0GCSqG
            SIb3DQEBAQUAA4IBDwAwggEKAoIBAQDdFzGxNSPcmPtjwxIJzgXpLaEx2L9lpatT
            3U68KjnM9q6OdUkO9qFBpBb0J/46qPohdyzj1esPXBZHCM5QGSWK2Iq9X/gMbSje
            incUjZvlwMbkLEvuxfDxYgVpGN9CLBDOWvmYD+eF9qYcaNR67JSEe7rEmYf6w7BN
            Umjih0YYNyR517GG1cBELDwk6TwChoZHn3cw4Z0QJjjvTbfOepGlid++KW52nkKw
            ogSBUfpYZP5Ve4lLF++5hMK4cfzUbeXwF3M/2w5PGE6pISdbShm/q69L2CepuDRs
            xexNb/fhAp4SKIpX/+zcOOtygUwDipmhmwyLusVnQ42sNpfIyFE5AgMBAAGjgYww
            gYkwHQYDVR0OBBYEFMyib1kprSIqi9TmhlmR6/83NYYjMB8GA1UdIwQYMBaAFMyi
            b1kprSIqi9TmhlmR6/83NYYjMA8GA1UdEwEB/wQFMAMBAf8wNgYDVR0RBC8wLYIa
            ZGlzY292ZXJ5LXRlc3QuZXhhbXBsZS5jb22CD2FsdC5leGFtcGxlLmNvbTANBgkq
            hkiG9w0BAQsFAAOCAQEAWpjBj0g2JzWbI9o2YuoxW6thNEvhPgYEzy/Rp3F4Pes3
            XcYhoR6G4EqZ6ZMoJ633U/7NorZshX08a1n5yj/a21Sq21S2rtVxcyHWgakfxlL6
            ygFpdo8nLIQ2rdcDYYYd47hGNeW8t9EsHanQ/F0NG4q4TRIoZaeH50Vpz5P5DABo
            VPh0u1HAdzzGP6Moxww86f5wu40+MIuFnG22Sdgf82wEgVFIM9UYVW3EyOz9pTAx
            zwtG/tbSJ34Qqe5BrUGYceWdHxxfRrCm+r2JYbpjZqKfOVmQWoo1N1PSgNy9kkgt
            b3TEYqsxNiOPSi1AbJVBIID16/6L7rVZuWS9dResMQ==
            -----END CERTIFICATE-----
            """;

    private static final String EXPECTED_FINGERPRINT =
            "8A:C5:CA:E3:54:14:F3:AB:61:F4:FA:5C:25:93:C6:BC:A0:2E:48:D9:B8:90:DC:31:51:91:00:A7:19:73:65:0D";
    private static final String EXPECTED_SERIAL = "664BA24BD789A4494F5C9F0F52F5C9D287DFE2E0";

    private final X509CertificateParser parser = new X509CertificateParser();

    @Test
    void readsEveryFieldTheInventoryNeeds() {
        Certificate certificate = parser.leafOf(PEM).orElseThrow();

        assertEquals("discovery-test.example.com", certificate.domain());
        assertEquals(EXPECTED_SERIAL, certificate.serialNumber());
        assertEquals("RSA", certificate.algorithm());
        assertEquals(2048, certificate.keySize());
        assertEquals("VALID", certificate.status());
        assertTrue(certificate.subject().contains("discovery-test.example.com"));
        assertTrue(certificate.expiryDate().isAfter(certificate.issuedDate()));
    }

    @Test
    void computesTheSameFingerprintOpensslDoes() {
        // This is the dedupe key. If it drifts, the same certificate found through
        // two services stops merging and the inventory double-counts.
        assertEquals(EXPECTED_FINGERPRINT, parser.leafOf(PEM).orElseThrow().fingerprint());
    }

    @Test
    void acceptsBase64WithoutPemArmourAsTheScanScriptsEmitIt() {
        String body = PEM.replace("-----BEGIN CERTIFICATE-----", "")
                         .replace("-----END CERTIFICATE-----", "")
                         .replaceAll("\\s", "");

        assertEquals(EXPECTED_FINGERPRINT, parser.fromBase64Der(body).orElseThrow().fingerprint());
    }

    @Test
    void takesTheLeafOfAChainRatherThanAnIntermediate() {
        // A chain file holds the server certificate first, then its issuers.
        List<Certificate> chain = parser.fromPem(PEM + PEM);

        assertEquals(2, chain.size());
        assertEquals("discovery-test.example.com", parser.leafOf(PEM + PEM).orElseThrow().domain());
    }

    @Test
    void returnsEmptyForAnythingThatIsNotACertificate() {
        assertEquals(Optional.empty(), parser.leafOf("not a certificate"));
        assertEquals(Optional.empty(), parser.fromBase64Der("!!!not base64!!!"));
        assertEquals(Optional.empty(), parser.fromDer(new byte[0]));
        assertEquals(Optional.empty(), parser.fromDer(null));
        assertTrue(parser.fromPem(null).isEmpty());
    }

    @Test
    void ignoresShellNoiseAroundThePemBlock() {
        String noisy = "warning: some directories were unreadable\n" + PEM + "\ndone\n";

        assertEquals(1, parser.fromPem(noisy).size());
    }
}
