package uk.co.ams.certplatform.application.port;

import uk.co.ams.certplatform.domain.enums.OsFamily;
import uk.co.ams.certplatform.domain.model.Certificate;
import uk.co.ams.certplatform.domain.model.ComputeCommand;
import uk.co.ams.certplatform.domain.model.ComputeCommandResult;
import uk.co.ams.certplatform.domain.model.ScanContext;

import java.util.List;

/**
 * One kind of certificate store that can live on a machine's disk: PEM files,
 * a Java keystore, the Windows certificate store, an nginx config's cert paths.
 *
 * <p><b>To support a new on-disk format</b> (item 15 of the roadmap, and anything
 * after it) add a {@code @Component} implementing this interface. Every
 * compute-side strategy picks it up automatically for the platforms it supports;
 * no strategy has to change.
 */
public interface ComputeCertificateSource {

    /** Stable name, used in log lines and as the certificate's source type. */
    String name();

    boolean supports(OsFamily osFamily);

    /** The script to run on the target. Should print one record per line and stay quiet on failure. */
    ComputeCommand command(ScanContext context, OsFamily osFamily);

    /** Turns the raw command output into certificates. Must tolerate partial and malformed output. */
    List<Certificate> parse(ScanContext context, ComputeCommandResult result);
}
