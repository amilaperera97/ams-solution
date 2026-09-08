package uk.co.ams.certplatform.domain.model;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;

public class ConnectionTestResult {
    private String status; // CONNECTED or FAILED
    private CloudProviderType provider;
    private String accountId;
    private String message;

    public ConnectionTestResult(String status, CloudProviderType provider, String accountId, String message) {
        this.status = status;
        this.provider = provider;
        this.accountId = accountId;
        this.message = message;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public CloudProviderType getProvider() { return provider; }
    public void setProvider(CloudProviderType provider) { this.provider = provider; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
