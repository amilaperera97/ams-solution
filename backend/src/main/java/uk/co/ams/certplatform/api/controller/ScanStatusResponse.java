package uk.co.ams.certplatform.api.controller;

import uk.co.ams.certplatform.domain.model.Scan;

/** The slice of a scan the UI polls while it is running. */
public record ScanStatusResponse(
        String scanId,
        String status,
        int progressPercent,
        int accountsTotal,
        int accountsCompleted,
        int certificatesDiscovered
) {

    public static ScanStatusResponse from(Scan scan) {
        return new ScanStatusResponse(
                scan.id(),
                scan.status().name(),
                scan.progressPercent(),
                scan.accountsTotal(),
                scan.accountsCompleted(),
                scan.certificatesDiscovered());
    }
}
