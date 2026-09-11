package uk.co.ams.certplatform.infrastructure.cloud.azure;

import uk.co.ams.certplatform.application.port.CloudProviderAdapter;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.domain.model.ConnectionTestResult;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.springframework.stereotype.Component;

@Component
public class AzureCloudProviderAdapter implements CloudProviderAdapter {

    private final CloudProviderProperties properties;

    public AzureCloudProviderAdapter(CloudProviderProperties properties) {
        this.properties = properties;
    }

    @Override
    public CloudProviderType providerType() {
        return CloudProviderType.AZURE;
    }

    @Override
    public ConnectionTestResult testConnection(Account account) {
        // Only AWS has a live implementation so far. Say so rather than reporting a
        // simulated success against what the operator believes is a real account.
        if (properties.isReal(CloudProviderType.AZURE)) {
            return ConnectionTestResult.failed(CloudProviderType.AZURE, account.accountId(),
                    "REAL mode is not implemented for AZURE yet; only AWS can be verified live.");
        }
        if (account.token() != null && !account.token().isBlank()) {
             return ConnectionTestResult.connected(CloudProviderType.AZURE, account.accountId(),
                     "Connection successful (simulated - provider mode is MOCK)");
        }
        return ConnectionTestResult.failed(CloudProviderType.AZURE, account.accountId(), "Unable to authenticate with cloud provider");
    }

}
