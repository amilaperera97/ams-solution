package uk.co.ams.certplatform.infrastructure.cloud.aws;

import uk.co.ams.certplatform.application.port.CloudProviderAdapter;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.domain.model.ConnectionTestResult;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityResponse;

@Component
public class AwsCloudProviderAdapter implements CloudProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(AwsCloudProviderAdapter.class);

    private final CloudProviderProperties properties;
    private final AwsClientFactory clientFactory;

    public AwsCloudProviderAdapter(CloudProviderProperties properties, AwsClientFactory clientFactory) {
        this.properties = properties;
        this.clientFactory = clientFactory;
    }

    @Override
    public CloudProviderType providerType() {
        return CloudProviderType.AWS;
    }

    @Override
    public ConnectionTestResult testConnection(Account account) {
        if (properties.isReal(CloudProviderType.AWS)) {
            return testConnectionForReal(account);
        }
        return testConnectionSimulated(account);
    }

    /**
     * Calls STS GetCallerIdentity with the account's stored credentials, then
     * checks the identity AWS reports back is the account we think it is.
     */
    private ConnectionTestResult testConnectionForReal(Account account) {
        String region = clientFactory.resolveRegion(account, null);
        try (StsClient sts = clientFactory.stsClient(account, region)) {
            GetCallerIdentityResponse identity = sts.getCallerIdentity();

            if (account.getAccountId() != null && !account.getAccountId().equals(identity.account())) {
                return new ConnectionTestResult("FAILED", CloudProviderType.AWS, account.getAccountId(),
                        "Credentials are valid but belong to AWS account " + identity.account()
                        + ", not " + account.getAccountId());
            }
            return new ConnectionTestResult("CONNECTED", CloudProviderType.AWS, identity.account(),
                    "Authenticated as " + identity.arn() + " in " + region);
        } catch (SdkException e) {
            log.warn("STS GetCallerIdentity failed for account {} in {}: {}", account.getId(), region, e.getMessage());
            return new ConnectionTestResult("FAILED", CloudProviderType.AWS, account.getAccountId(),
                    "AWS rejected the credentials: " + rootMessage(e));
        } catch (RuntimeException e) {
            log.warn("Connection test could not run for account {}: {}", account.getId(), e.getMessage());
            return new ConnectionTestResult("FAILED", CloudProviderType.AWS, account.getAccountId(), rootMessage(e));
        }
    }

    /** Shape-only check used by the dev profile, where no real AWS account exists. */
    private ConnectionTestResult testConnectionSimulated(Account account) {
        if (account.getAccountId() != null && account.getAccountId().length() == 12) {
            return new ConnectionTestResult("CONNECTED", CloudProviderType.AWS, account.getAccountId(),
                    "Connection successful (simulated - provider mode is MOCK)");
        }
        return new ConnectionTestResult("FAILED", CloudProviderType.AWS, account.getAccountId(),
                "Unable to authenticate with cloud provider");
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message != null ? message : cause.getClass().getSimpleName();
    }
}
