package uk.co.ams.certplatform.api.controller;

import uk.co.ams.certplatform.domain.model.Scan;

public class ScanStatusResponse {
    private String scanId;
    private String status;
    private int progressPercent;
    private int accountsTotal;
    private int accountsCompleted;
    private int certificatesDiscovered;

    public ScanStatusResponse(Scan scan) {
        this.scanId = scan.getId();
        this.status = scan.getStatus().name();
        this.progressPercent = scan.getProgressPercent();
        this.accountsTotal = scan.getAccountsTotal();
        this.accountsCompleted = scan.getAccountsCompleted();
        this.certificatesDiscovered = scan.getCertificatesDiscovered();
    }

    public String getScanId() { return scanId; }
    public String getStatus() { return status; }
    public int getProgressPercent() { return progressPercent; }
    public int getAccountsTotal() { return accountsTotal; }
    public int getAccountsCompleted() { return accountsCompleted; }
    public int getCertificatesDiscovered() { return certificatesDiscovered; }
}
