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
        Certificate cert1 = new Certificate();
        cert1.setFingerprint("AA:BB:CC");
        cert1.setDomain("example.com");
        CertificateUsage usage1 = new CertificateUsage();
        usage1.setService("ACM");
        cert1.addUsage(usage1);

        Certificate cert2 = new Certificate();
        cert2.setFingerprint("AA:BB:CC");
        cert2.setDomain("example.com");
        CertificateUsage usage2 = new CertificateUsage();
        usage2.setService("ALB");
        cert2.addUsage(usage2);

        List<Certificate> resolved = resolver.resolve(Arrays.asList(cert1, cert2));

        assertEquals(1, resolved.size());
        assertEquals("AA:BB:CC", resolved.get(0).getFingerprint());
        assertEquals(2, resolved.get(0).getUsages().size());
        assertTrue(resolved.get(0).getUsages().stream().anyMatch(u -> "ACM".equals(u.getService())));
        assertTrue(resolved.get(0).getUsages().stream().anyMatch(u -> "ALB".equals(u.getService())));
    }
    
    @Test
    void shouldNotDeduplicateCertificatesWithDifferentFingerprints() {
        Certificate cert1 = new Certificate();
        cert1.setFingerprint("AA:BB:CC");
        
        Certificate cert2 = new Certificate();
        cert2.setFingerprint("DD:EE:FF");
        
        List<Certificate> resolved = resolver.resolve(Arrays.asList(cert1, cert2));

        assertEquals(2, resolved.size());
    }
}
