package uk.co.ams.certplatform.infrastructure.persistence.repository;

import uk.co.ams.certplatform.domain.model.ApplicationMetadata;
import uk.co.ams.certplatform.domain.model.Certificate;
import uk.co.ams.certplatform.domain.model.CertificateUsage;
import uk.co.ams.certplatform.domain.model.OperatingSystemMetadata;
import uk.co.ams.certplatform.domain.model.ResourceTag;
import uk.co.ams.certplatform.infrastructure.persistence.entity.CertificateEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Usages and tags are stored as JSON blobs rather than columns, so they are the
 * one part of a certificate that survives a round trip only if Jackson can both
 * write and read the model. These pin that, and with it the nested records a
 * usage carries.
 */
class CertificateRepositoryAdapterTest {

    @Mock
    private JpaCertificateRepository jpaRepository;

    private CertificateRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adapter = new CertificateRepositoryAdapter(jpaRepository);
        when(jpaRepository.save(any(CertificateEntity.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void shouldRoundTripUsagesAndTagsThroughTheirJsonColumns() {
        Certificate saved = adapter.save(certificateWithTwoUsages());

        assertEquals(2, saved.usages().size());
        assertEquals(List.of("ACM", "ALB"),
                saved.usages().stream().map(CertificateUsage::service).toList());
        assertEquals("ATTACHED", saved.usages().get(1).usageType());
        assertEquals("eu-west-2", saved.usages().get(1).region());

        assertEquals(1, saved.tags().size());
        assertEquals("owner", saved.tags().get(0).key());
        assertEquals("platform-team", saved.tags().get(0).value());
    }

    @Test
    void shouldRoundTripTheMetadataNestedInsideAUsage() {
        CertificateUsage onDisk = CertificateUsage.builder()
                .service("EC2")
                .usageType("ON_DISK")
                .os(OperatingSystemMetadata.of("LINUX", "Amazon Linux", "2023"))
                .application(new ApplicationMetadata("checkout", "java", "21",
                        "spring-boot", "3.4.2", "checkout-api", "SYSTEMD"))
                .build();

        Certificate saved = adapter.save(Certificate.builder()
                .domain("disk.example.com")
                .usage(onDisk)
                .build());

        CertificateUsage restored = saved.usages().get(0);
        assertNotNull(restored.os());
        assertEquals("Amazon Linux", restored.os().name());
        assertEquals("2023", restored.os().version());
        assertNotNull(restored.application());
        assertEquals("checkout", restored.application().applicationName());
        assertEquals("SYSTEMD", restored.application().deploymentType());
    }

    @Test
    void shouldGiveACertificateAnIdAndTimestampWithoutMutatingTheCallersCopy() {
        Certificate original = Certificate.builder().domain("example.com").build();

        Certificate saved = adapter.save(original);

        assertTrue(saved.id().startsWith("cert-"));
        assertNotNull(saved.createdAt());
        assertNull(original.id(), "the caller's certificate is immutable and untouched");
        assertNull(original.createdAt());
    }

    private static Certificate certificateWithTwoUsages() {
        return Certificate.builder()
                .domain("example.com")
                .fingerprint("AA:BB:CC")
                .usage(CertificateUsage.builder().service("ACM").usageType("ISSUED").build())
                .usage(CertificateUsage.builder()
                        .service("ALB")
                        .usageType("ATTACHED")
                        .region("eu-west-2")
                        .resource("arn:aws:elasticloadbalancing:eu-west-2:123456789012:loadbalancer/app/web/abc")
                        .build())
                .tag(new ResourceTag("owner", "platform-team"))
                .build();
    }
}
