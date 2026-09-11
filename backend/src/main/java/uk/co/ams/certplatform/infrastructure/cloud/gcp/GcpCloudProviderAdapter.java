package uk.co.ams.certplatform.infrastructure.cloud.gcp;

import uk.co.ams.certplatform.application.port.CloudProviderAdapter;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.domain.model.ConnectionTestResult;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.springframework.stereotype.Component;

@Component
public class GcpCloudProviderAdapter implements CloudProviderAdapter {

    private final CloudProviderProperties properties;

    public GcpCloudProviderAdapter(CloudProviderProperties properties) {
        this.properties = properties;
    }

    @Override
    public CloudProviderType providerType() {
        return CloudProviderType.GCP;
    }

    @Override
    public ConnectionTestResult testConnection(Account account) {
        // Only AWS has a live implementation so far. Say so rather than reporting a
        // simulated success against what the operator believes is a real account.
        if (properties.isReal(CloudProviderType.GCP)) {
            return ConnectionTestResult.failed(CloudProviderType.GCP, account.accountId(),
                    "REAL mode is not implemented for GCP yet; only AWS can be verified live.");
        }
        if (account.token() != null && !account.token().isBlank()) {
             return ConnectionTestResult.connected(CloudProviderType.GCP, account.accountId(),
                     "Connection successful (simulated - provider mode is MOCK)");
        }
        return ConnectionTestResult.failed(CloudProviderType.GCP, account.accountId(), "Unable to authenticate with cloud provider");
    }

}
