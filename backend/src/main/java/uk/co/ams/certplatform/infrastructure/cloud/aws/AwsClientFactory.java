package uk.co.ams.certplatform.infrastructure.cloud.aws;

import uk.co.ams.certplatform.domain.enums.AccountAuthType;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Builds AWS SDK clients from the credentials stored against an {@link Account}.
 *
 * <p>There is deliberately no per-service factory method. Onboarding a service
 * means calling {@code client(FooClient::builder, account, region)} from the new
 * strategy - nothing here changes. Clients are {@link AutoCloseable}; callers
 * must use try-with-resources.
 *
 * <p>Assumed-role credentials are cached per account and reused until shortly
 * before they expire. Without that, a fifteen-service scan across five regions
 * would perform seventy-five AssumeRole calls for a single account and would be
 * throttled by STS long before AWS ran out of certificates to report.
 */
@Component
public class AwsClientFactory {

    private static final Logger log = LoggerFactory.getLogger(AwsClientFactory.class);

    /** Renew this far ahead of expiry so an in-flight call cannot outlive its credentials. */
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(5);
    private static final int ASSUME_ROLE_DURATION_SECONDS = 3600;

    private final CloudProviderProperties properties;
    private final Map<String, CachedCredentials> assumedRoleCache = new ConcurrentHashMap<>();

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
        if (account != null && account.region() != null && !account.region().isBlank()) {
            return account.region();
        }
        return settings().getDefaultRegion();
    }

    /**
     * The one entry point for every AWS service client.
     *
     * <pre>{@code
     * try (AcmClient acm = clientFactory.client(AcmClient::builder, account, region)) { ... }
     * }</pre>
     */
    public <B extends AwsClientBuilder<B, C>, C> C client(Supplier<B> builderSupplier, Account account, String region) {
        String effectiveRegion = resolveRegion(account, region);
        return configure(builderSupplier.get(), effectiveRegion)
                .credentialsProvider(credentialsFor(account, effectiveRegion))
                .build();
    }

    public StsClient stsClient(Account account, String region) {
        return client(StsClient::builder, account, region);
    }

    private <B extends AwsClientBuilder<B, ?>> B configure(B builder, String region) {
        CloudProviderProperties.ProviderSettings settings = settings();
        builder.region(Region.of(region))
               .overrideConfiguration(ClientOverrideConfiguration.builder()
                       .apiCallTimeout(Duration.ofSeconds(settings.getApiTimeoutSeconds()))
                       // Adaptive retry backs off on throttling rather than hammering a
                       // rate-limited account, which matters once a scan fans out over
                       // fifteen services and several regions at once.
                       .retryStrategy(AwsRetryStrategy.adaptiveRetryStrategy())
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
        AccountAuthType authType = account.authType();
        if (authType == null) {
            throw new IllegalStateException("Account " + account.id() + " has no auth type configured");
        }
        return switch (authType) {
            case ACCESS_KEY -> StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(requireAccessKeyId(account), requireSecretAccessKey(account)));
            case IAM_ROLE -> cachedAssumeRole(account, region);
            case TOKEN -> throw new IllegalStateException(
                    "TOKEN auth cannot be used against real AWS. Reconfigure account " + account.id()
                    + " with ACCESS_KEY or IAM_ROLE.");
        };
    }

    private AwsCredentialsProvider cachedAssumeRole(Account account, String region) {
        String cacheKey = account.id() + "|" + account.roleArn();
        CachedCredentials cached = assumedRoleCache.get(cacheKey);
        if (cached != null && cached.isUsable()) {
            return cached.provider();
        }
        synchronized (assumedRoleCache) {
            cached = assumedRoleCache.get(cacheKey);
            if (cached != null && cached.isUsable()) {
                return cached.provider();
            }
            CachedCredentials fresh = assumeRole(account, region);
            assumedRoleCache.put(cacheKey, fresh);
            return fresh.provider();
        }
    }

    private CachedCredentials assumeRole(Account account, String region) {
        try (StsClient base = configure(StsClient.builder(), region)
                .credentialsProvider(baseCredentialsFor(account))
                .build()) {
            AssumeRoleResponse response = base.assumeRole(assumeRoleRequestFor(account));
            Credentials issued = response.credentials();
            log.debug("Assumed {} for account {}, expires {}", account.roleArn(), account.id(), issued.expiration());
            return new CachedCredentials(
                    StaticCredentialsProvider.create(AwsSessionCredentials.create(
                            issued.accessKeyId(), issued.secretAccessKey(), issued.sessionToken())),
                    issued.expiration());
        }
    }

    /**
     * The identity that calls STS AssumeRole for an IAM_ROLE account.
     *
     * <p>Access keys stored on the account are optional bootstrap credentials; they
     * are used only when present. Otherwise we fall back to the SDK's
     * {@link DefaultCredentialsProvider} chain, which is what makes IAM_ROLE work on
     * a developer machine: it resolves, in order, environment credentials
     * ({@code AWS_ACCESS_KEY_ID}/{@code AWS_SECRET_ACCESS_KEY}/{@code AWS_SESSION_TOKEN}),
     * the Java system properties, the web identity token file, then the profile named
     * by {@code AWS_PROFILE} in {@code ~/.aws/credentials} and {@code ~/.aws/config}
     * - including SSO profiles, provided {@code aws sso login} has been run - and
     * finally container/instance metadata when running inside AWS.
     *
     * <p>Whichever identity the chain resolves must be allowed to call
     * {@code sts:AssumeRole} on the account's role ARN, and that role's trust policy
     * must name the identity (or its account) as a principal. Without both, AWS
     * answers AccessDenied and the scan or connection test fails.
     */
    AwsCredentialsProvider baseCredentialsFor(Account account) {
        if (hasAccessKey(account)) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(account.accessKeyId(), account.secretAccessKey()));
        }
        return DefaultCredentialsProvider.create();
    }

    /** Built separately from the call so the external-id and session-name rules can be tested offline. */
    AssumeRoleRequest assumeRoleRequestFor(Account account) {
        if (account.roleArn() == null || account.roleArn().isBlank()) {
            throw new IllegalStateException("Account " + account.id() + " is IAM_ROLE but has no role ARN");
        }
        AssumeRoleRequest.Builder request = AssumeRoleRequest.builder()
                .roleArn(account.roleArn())
                .roleSessionName("certplatform-" + shortId(account.id()))
                .durationSeconds(ASSUME_ROLE_DURATION_SECONDS);
        // Required when the target role's trust policy sets a sts:ExternalId condition.
        if (account.externalId() != null && !account.externalId().isBlank()) {
            request.externalId(account.externalId());
        }
        return request.build();
    }

    /** Drops any cached session for an account, so a credential change takes effect at once. */
    public void evict(String accountId) {
        assumedRoleCache.keySet().removeIf(key -> key.startsWith(accountId + "|"));
    }

    private record CachedCredentials(AwsCredentialsProvider provider, Instant expiresAt) {
        boolean isUsable() {
            return expiresAt != null && Instant.now().plus(EXPIRY_MARGIN).isBefore(expiresAt);
        }
    }

    private static boolean hasAccessKey(Account account) {
        return account.accessKeyId() != null && !account.accessKeyId().isBlank()
                && account.secretAccessKey() != null && !account.secretAccessKey().isBlank();
    }

    private static String requireAccessKeyId(Account account) {
        if (account.accessKeyId() == null || account.accessKeyId().isBlank()) {
            throw new IllegalStateException("Account " + account.id() + " is ACCESS_KEY but has no access key id");
        }
        return account.accessKeyId();
    }

    private static String requireSecretAccessKey(Account account) {
        if (account.secretAccessKey() == null || account.secretAccessKey().isBlank()) {
            throw new IllegalStateException("Account " + account.id() + " is ACCESS_KEY but has no secret access key");
        }
        return account.secretAccessKey();
    }

    /** Role session names are limited to 64 chars, so keep only the tail of the account id. */
    private static String shortId(String accountId) {
        if (accountId == null) return "scan";
        return accountId.length() <= 32 ? accountId : accountId.substring(accountId.length() - 32);
    }
}
