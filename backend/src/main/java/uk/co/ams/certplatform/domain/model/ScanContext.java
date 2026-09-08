package uk.co.ams.certplatform.domain.model;

public class ScanContext {
    private String scanId;
    private Account account;
    private String region;
    private String service;

    public ScanContext(String scanId, Account account) {
        this.scanId = scanId;
        this.account = account;
    }
    
    public ScanContext(String scanId, Account account, String region, String service) {
        this.scanId = scanId;
        this.account = account;
        this.region = region;
        this.service = service;
    }

    public String getScanId() { return scanId; }
    public Account getAccount() { return account; }
    
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    
    public String getService() { return service; }
    public void setService(String service) { this.service = service; }
}
