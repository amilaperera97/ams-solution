package uk.co.ams.certplatform.infrastructure.compute;

import uk.co.ams.certplatform.infrastructure.compute.source.ComputeOutput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComputeOutputTest {

    @Test
    void readsOneRecordPerCertLine() {
        List<ComputeOutput.Record> records = ComputeOutput.parse(
                "CERT|/etc/nginx/site.pem|AAAA\nCERT|/opt/ssl/api.crt|BBBB\n");

        assertEquals(2, records.size());
        assertEquals("/etc/nginx/site.pem", records.get(0).location());
        assertEquals("BBBB", records.get(1).base64Der());
    }

    @Test
    void ignoresAnythingThatIsNotACertLine() {
        // Hardened images print motd banners and sudo warnings; none of it is ours.
        List<ComputeOutput.Record> records = ComputeOutput.parse("""
                find: '/etc/haproxy': Permission denied
                CERT|/etc/nginx/site.pem|AAAA
                sudo: unable to resolve host
                """);

        assertEquals(1, records.size());
    }

    @Test
    void keepsAWindowsStorePathContainingBackslashes() {
        List<ComputeOutput.Record> records = ComputeOutput.parse(
                "CERT|Cert:\\LocalMachine\\My\\ABC123|AAAA");

        assertEquals("Cert:\\LocalMachine\\My\\ABC123", records.get(0).location());
    }

    @Test
    void skipsALineWithNoCertificateBody() {
        assertTrue(ComputeOutput.parse("CERT|/etc/nginx/site.pem|").isEmpty());
        assertTrue(ComputeOutput.parse("CERT|/etc/nginx/site.pem").isEmpty());
    }

    @Test
    void toleratesEmptyOutput() {
        assertTrue(ComputeOutput.parse(null).isEmpty());
        assertTrue(ComputeOutput.parse("   ").isEmpty());
    }
}
