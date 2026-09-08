package uk.co.ams.certplatform.infrastructure.cloud.aws;

import uk.co.ams.certplatform.domain.enums.AccountAuthType;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.acm.AcmClient;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

import java.net.URI;
import java.time.Duration;

/**
 * Builds AWS SDK clients from the credentials stored against an {@link Account}.
 *
 * Clients are created per call rather than cached, because each account carries
 * its own credentials and region. They are {@link AutoCloseable} - callers must
 * use try-with-resources.
 */
@Component
public class AwsClientFactory {

    private final CloudProviderProperties properties;

    public AwsClientFactory(CloudProviderProperties properties) {
        this.properties = properties;
    }

    private CloudProviderProperties.ProviderSettings settings() {
        return properties.settingsFor(CloudProviderType.AWS);
    }

    /** Scan requests may carry no region (or the literal "default"); fall back to the account, then config. */
    public String resolveRegion(Account account, String requestedRegion) {
        if (requestedRegion != null && !requestedRegion.isBlank() && !"default".equalsIgnoreCase(requestedRegion)) {
            return requestedRegion;
        }
        if (account != null && account.getRegion() != null && !account.getRegion().isBlank()) {
            return account.getRegion();
        }
        return settings().getDefaultRegion();
    }

    public StsClient stsClient(Account account, String region) {
        return configure(StsClient.builder(), region)
                .credentialsProvider(credentialsFor(account, region))
                .build();
    }

    public AcmClient acmClient(Account account, String region) {
        return configure(AcmClient.builder(), region)
                .credentialsProvider(credentialsFor(account, region))
                .build();
    }

    private <B extends AwsClientBuilder<B, ?>> B configure(B builder, String region) {
        CloudProviderProperties.ProviderSettings settings = settings();
        builder.region(Region.of(region))
               .overrideConfiguration(ClientOverrideConfiguration.builder()
                       .apiCallTimeout(Duration.ofSeconds(settings.getApiTimeoutSeconds()))
                       .build());
        // Present for LocalStack or a recorded stub; absent when talking to real AWS.
        if (settings.getEndpoint() != null && !settings.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(settings.getEndpoint()));
        }
        return builder;
    }

    /**
     * Turns the account's stored credentials into a provider the SDK can use.
     * IAM_ROLE performs the AssumeRole up front and returns the resulting session
     * credentials, so no STS client has to stay open for the life of the call.
     */
    AwsCredentialsProvider credentialsFor(Account account, String region) {
        AccountAuthType authType = account.getAuthType();
        if (authType == null) {
            throw new IllegalStateException("Account " + account.getId() + " has no auth type configured");
        }
        return switch (authType) {
            case ACCESS_KEY -> StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(requireAccessKeyId(account), requireSecretAccessKey(account)));
            case IAM_ROLE -> assumeRole(account, region);
            case TOKEN -> throw new IllegalStateException(
                    "TOKEN auth cannot be used against real AWS. Reconfigure account " + account.getId()
                    + " with ACCESS_KEY or IAM_ROLE.");
        };
    }

    private AwsCredentialsProvider assumeRole(Account account, String region) {
        if (account.getRoleArn() == null || account.getRoleArn().isBlank()) {
            throw new IllegalStateException("Account " + account.getId() + " is IAM_ROLE but has no role ARN");
        }
        AwsCredentialsProvider baseCredentials = hasAccessKey(account)
                ? StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(account.getAccessKeyId(), account.getSecretAccessKey()))
                : DefaultCredentialsProvider.create();

        AssumeRoleRequest.Builder request = AssumeRoleRequest.builder()
                .roleArn(account.getRoleArn())
                .roleSessionName("certplatform-" + shortId(account.getId()))
                .durationSeconds(3600);
        if (account.getExternalId() != null && !account.getExternalId().isBlank()) {
            request.externalId(account.getExternalId());
        }

        try (StsClient base = configure(StsClient.builder(), region)
                .credentialsProvider(baseCredentials)
                .build()) {
            AssumeRoleResponse response = base.assumeRole(request.build());
            Credentials issued = response.credentials();
            return StaticCredentialsProvider.create(AwsSessionCredentials.create(
                    issued.accessKeyId(), issued.secretAccessKey(), issued.sessionToken()));
        }
    }

    private static boolean hasAccessKey(Account account) {
        return account.getAccessKeyId() != null && !account.getAccessKeyId().isBlank()
                && account.getSecretAccessKey() != null && !account.getSecretAccessKey().isBlank();
    }

    private static String requireAccessKeyId(Account account) {
        if (account.getAccessKeyId() == null || account.getAccessKeyId().isBlank()) {
            throw new IllegalStateException("Account " + account.getId() + " is ACCESS_KEY but has no access key id");
        }
        return account.getAccessKeyId();
    }

    private static String requireSecretAccessKey(Account account) {
        if (account.getSecretAccessKey() == null || account.getSecretAccessKey().isBlank()) {
            throw new IllegalStateException("Account " + account.getId() + " is ACCESS_KEY but has no secret access key");
        }
        return account.getSecretAccessKey();
    }

    /** Role session names are limited to 64 chars, so keep only the tail of the account id. */
    private static String shortId(String accountId) {
        if (accountId == null) return "scan";
        return accountId.length() <= 32 ? accountId : accountId.substring(accountId.length() - 32);
    }
}
