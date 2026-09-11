package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.domain.model.Certificate;
import uk.co.ams.certplatform.domain.model.CertificateUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CertificateIdentityResolverTest {

    private CertificateIdentityResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new CertificateIdentityResolver();
    }

    @Test
    void shouldDeduplicateCertificatesWithSameFingerprint() {
        Certificate cert1 = Certificate.builder()
                .fingerprint("AA:BB:CC")
                .domain("example.com")
                .usage(CertificateUsage.builder().service("ACM").build())
                .build();

        Certificate cert2 = Certificate.builder()
                .fingerprint("AA:BB:CC")
                .domain("example.com")
                .usage(CertificateUsage.builder().service("ALB").build())
                .build();

        List<Certificate> resolved = resolver.resolve(Arrays.asList(cert1, cert2));

        assertEquals(1, resolved.size());
        assertEquals("AA:BB:CC", resolved.get(0).fingerprint());
        assertEquals(2, resolved.get(0).usages().size());
        assertTrue(resolved.get(0).usages().stream().anyMatch(u -> "ACM".equals(u.service())));
        assertTrue(resolved.get(0).usages().stream().anyMatch(u -> "ALB".equals(u.service())));
    }
    
    @Test
    void shouldNotDeduplicateCertificatesWithDifferentFingerprints() {
        Certificate cert1 = Certificate.builder().fingerprint("AA:BB:CC").build();
        Certificate cert2 = Certificate.builder().fingerprint("DD:EE:FF").build();


        List<Certificate> resolved = resolver.resolve(Arrays.asList(cert1, cert2));

        assertEquals(2, resolved.size());
    }
}
