package uk.co.ams.certplatform.infrastructure.discovery.support;

import uk.co.ams.certplatform.domain.model.Certificate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns raw X.509 material into the platform's {@link Certificate} model.
 *
 * <p>Shared by every strategy that gets its hands on actual certificate bytes -
 * IAM server certificates, PEM files on an instance, secrets, S3 objects,
 * keystores. Centralising it means fingerprints are computed the same way
 * everywhere, which is what lets {@code CertificateIdentityResolver} recognise
 * the same certificate found through two different services.
 */
@Component
public class X509CertificateParser {

    private static final Logger log = LoggerFactory.getLogger(X509CertificateParser.class);

    private static final Pattern PEM_BLOCK = Pattern.compile(
            "-----BEGIN CERTIFICATE-----(.*?)-----END CERTIFICATE-----", Pattern.DOTALL);
    private static final Pattern SUBJECT_CN = Pattern.compile("CN=([^,]+)");

    /** Parses one DER-encoded certificate. Empty when the bytes are not a certificate. */
    public Optional<Certificate> fromDer(byte[] der) {
        if (der == null || der.length == 0) return Optional.empty();
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate x509 = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(der));
            return Optional.of(toDomain(x509));
        } catch (Exception e) {
            log.debug("Not a parseable X.509 certificate: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /** Parses base64 DER with no PEM armour - the form the compute scan scripts emit. */
    public Optional<Certificate> fromBase64Der(String base64) {
        if (base64 == null || base64.isBlank()) return Optional.empty();
        try {
            return fromDer(Base64.getMimeDecoder().decode(base64.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses every certificate in a PEM document. Chains are common (leaf then
     * intermediates), so callers that only want the server certificate should
     * take the first element - see {@link #leafOf(String)}.
     */
    public List<Certificate> fromPem(String pem) {
        List<Certificate> certificates = new ArrayList<>();
        if (pem == null || pem.isBlank()) return certificates;

        Matcher matcher = PEM_BLOCK.matcher(pem);
        boolean sawBlock = false;
        while (matcher.find()) {
            sawBlock = true;
            fromBase64Der(matcher.group(1)).ifPresent(certificates::add);
        }
        if (!sawBlock) {
            // Some sources store the base64 body without the armour.
            fromBase64Der(pem).ifPresent(certificates::add);
        }
        return certificates;
    }

    /** The end-entity certificate of a PEM chain, which is by convention the first block. */
    public Optional<Certificate> leafOf(String pem) {
        List<Certificate> chain = fromPem(pem);
        return chain.isEmpty() ? Optional.empty() : Optional.of(chain.get(0));
    }

    /** Populates a fresh domain certificate from a parsed JDK certificate. */
    public Certificate toDomain(X509Certificate x509) {
        return Certificate.builder()
                .subject(x509.getSubjectX500Principal().getName())
                .issuer(x509.getIssuerX500Principal().getName())
                .serialNumber(hexSerial(x509.getSerialNumber()))
                .fingerprint(sha256Fingerprint(x509))
                .issuedDate(x509.getNotBefore() != null ? x509.getNotBefore().toInstant() : null)
                .expiryDate(x509.getNotAfter() != null ? x509.getNotAfter().toInstant() : null)
                .algorithm(keyAlgorithmOf(x509))
                .keySize(keySizeOf(x509.getPublicKey()))
                .domain(primaryDomainOf(x509))
                .status(statusOf(x509))
                .build();
    }

    /** Uppercase hex, zero-padded to an even length, matching how ACM reports serials. */
    public static String hexSerial(BigInteger serial) {
        if (serial == null) return null;
        String hex = serial.toString(16).toUpperCase(Locale.ROOT);
        return hex.length() % 2 == 0 ? hex : "0" + hex;
    }

    /**
     * SHA-256 of the DER encoding, colon-separated. This is the identity the
     * deduplicator keys on, so it must be stable across every discovery path.
     */
    public static String sha256Fingerprint(X509Certificate x509) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(x509.getEncoded());
            StringBuilder hex = new StringBuilder(digest.length * 3);
            for (byte b : digest) {
                if (hex.length() > 0) hex.append(':');
                hex.append(String.format("%02X", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Prefers the subject CN, falling back to the first DNS subject alternative name. */
    private static String primaryDomainOf(X509Certificate x509) {
        Matcher cn = SUBJECT_CN.matcher(x509.getSubjectX500Principal().getName());
        if (cn.find()) return cn.group(1).trim();
        for (String san : subjectAlternativeNames(x509)) return san;
        return null;
    }

    public static List<String> subjectAlternativeNames(X509Certificate x509) {
        List<String> names = new ArrayList<>();
        try {
            Collection<List<?>> sans = x509.getSubjectAlternativeNames();
            if (sans == null) return names;
            for (List<?> san : sans) {
                // Type 2 is dNSName; the value is at index 1.
                if (san.size() >= 2 && Integer.valueOf(2).equals(san.get(0)) && san.get(1) instanceof String dns) {
                    names.add(dns);
                }
            }
        } catch (Exception e) {
            log.debug("Could not read subject alternative names: {}", e.getMessage());
        }
        return names;
    }

    private static String keyAlgorithmOf(X509Certificate x509) {
        PublicKey key = x509.getPublicKey();
        return key != null ? key.getAlgorithm() : null;
    }

    private static Integer keySizeOf(PublicKey key) {
        if (key instanceof RSAPublicKey rsa) return rsa.getModulus().bitLength();
        if (key instanceof ECPublicKey ec) return ec.getParams().getCurve().getField().getFieldSize();
        if (key instanceof DSAPublicKey dsa) return dsa.getParams().getP().bitLength();
        return null;
    }

    /** Validity judged against the clock, because a file on disk carries no status field. */
    private static String statusOf(X509Certificate x509) {
        try {
            x509.checkValidity();
            return "VALID";
        } catch (java.security.cert.CertificateExpiredException e) {
            return "EXPIRED";
        } catch (java.security.cert.CertificateNotYetValidException e) {
            return "NOT_YET_VALID";
        }
    }
}
