package uk.co.ams.certplatform.infrastructure.discovery.aws;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Certificate;
import uk.co.ams.certplatform.domain.model.CertificateUsage;
import uk.co.ams.certplatform.domain.model.DiscoveryResult;
import uk.co.ams.certplatform.domain.model.DiscoveryServiceDescriptor;
import uk.co.ams.certplatform.domain.model.ResourceTag;
import uk.co.ams.certplatform.domain.model.ScanContext;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AbstractAwsDiscoveryStrategy;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsCertificateReferenceResolver;
import uk.co.ams.certplatform.infrastructure.cloud.aws.AwsClientFactory;
import uk.co.ams.certplatform.infrastructure.discovery.support.SimulatedCertificateFactory;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.apigateway.ApiGatewayClient;
import software.amazon.awssdk.services.apigateway.model.GetDomainNamesRequest;
import software.amazon.awssdk.services.apigatewayv2.ApiGatewayV2Client;
import software.amazon.awssdk.services.apigatewayv2.model.DomainNameConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Service 6: certificates on API Gateway custom domain names.
 *
 * <p>API Gateway is two products behind one name. REST APIs use the v1 API and
 * HTTP/WebSocket APIs use v2, and a domain created through one is invisible to
 * the other. Both are walked here under a single catalogue entry, because from
 * an operator's point of view "API Gateway" is one thing to tick.
 *
 * <p>Edge-optimised v1 domains hold their certificate in us-east-1 while the
 * domain itself is regional; the resolver reads the region from the ARN, so
 * both {@code certificateArn} and {@code regionalCertificateArn} resolve
 * correctly without the caller thinking about it.
 */
@Component
public class AwsApiGatewayDiscoveryStrategy extends AbstractAwsDiscoveryStrategy {

    private static final DiscoveryServiceDescriptor DESCRIPTOR =
            DiscoveryServiceDescriptor.builder(CloudProviderType.AWS, "API_GATEWAY", "API Gateway")
                    .phase(1)
                    .aliases("APIGATEWAY", "API GATEWAY", "APIGW", "API_GATEWAY_V2")
                    .requiredPermissions("apigateway:GET", "acm:DescribeCertificate", "iam:GetServerCertificate")
                    .build();

    private final AwsCertificateReferenceResolver resolver;

    public AwsApiGatewayDiscoveryStrategy(CloudProviderProperties providerProperties,
                                          SimulatedCertificateFactory simulator,
                                          AwsClientFactory clientFactory,
                                          AwsCertificateReferenceResolver resolver) {
        super(providerProperties, simulator, clientFactory);
        this.resolver = resolver;
    }

    @Override
    public DiscoveryServiceDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    protected void discoverLive(ScanContext context, DiscoveryResult result) {
        Map<String, Certificate> byArn = new LinkedHashMap<>();
        int domains = collectRestDomains(context, result, byArn) + collectHttpDomains(context, result, byArn);

        byArn.values().forEach(result::addCertificate);
        result.settle("Discovered " + byArn.size() + " certificate(s) across " + domains
                + " API Gateway domain(s) in " + effectiveRegion(context));
    }

    /** REST APIs: the v1 control plane, where a domain may carry both an edge and a regional certificate. */
    private int collectRestDomains(ScanContext context, DiscoveryResult result, Map<String, Certificate> byArn) {
        int domains = 0;
        try (ApiGatewayClient apiGateway = client(ApiGatewayClient::builder, context)) {
            for (software.amazon.awssdk.services.apigateway.model.DomainName domain :
                    apiGateway.getDomainNamesPaginator(GetDomainNamesRequest.builder().build()).items()) {
                domains++;
                record(context, byArn, domain.certificateArn(), domain.domainName(), "EDGE", domain.tags());
                record(context, byArn, domain.regionalCertificateArn(), domain.domainName(), "REGIONAL", domain.tags());
            }
        } catch (SdkException e) {
            // v1 and v2 fail independently; losing one must not hide the other.
            log.warn("API Gateway (REST) domain listing failed: {}", e.getMessage());
            result.addError("apigateway v1: " + e.getMessage());
        }
        return domains;
    }

    /** HTTP and WebSocket APIs: the v2 control plane, one configuration per endpoint type. */
    private int collectHttpDomains(ScanContext context, DiscoveryResult result, Map<String, Certificate> byArn) {
        int domains = 0;
        try (ApiGatewayV2Client apiGateway = client(ApiGatewayV2Client::builder, context)) {
            String nextToken = null;
            do {
                var response = apiGateway.getDomainNames(
                        software.amazon.awssdk.services.apigatewayv2.model.GetDomainNamesRequest.builder()
                                .nextToken(nextToken)
                                .build());
                for (software.amazon.awssdk.services.apigatewayv2.model.DomainName domain : response.items()) {
                    domains++;
                    for (DomainNameConfiguration configuration : domain.domainNameConfigurations()) {
                        record(context, byArn, configuration.certificateArn(), domain.domainName(),
                                configuration.endpointTypeAsString(), domain.tags());
                    }
                }
                nextToken = response.nextToken();
            } while (nextToken != null && !nextToken.isBlank());
        } catch (SdkException e) {
            log.warn("API Gateway (HTTP) domain listing failed: {}", e.getMessage());
            result.addError("apigatewayv2: " + e.getMessage());
        }
        return domains;
    }

    private void record(ScanContext context, Map<String, Certificate> byArn, String certificateArn,
                        String domainName, String endpointType, Map<String, String> tags) {
        if (certificateArn == null || certificateArn.isBlank()) return;

        Certificate certificate = byArn.computeIfAbsent(certificateArn, arn ->
                resolver.resolve(context, arn)
                        .orElseGet(() -> resolver.unresolved(context, arn, descriptor().key())));

        CertificateUsage usage = usage(context, descriptor().key(), certificateArn, "Custom domain", "ATTACHED");
        usage.setResource(domainName);
        certificate.addUsage(usage);

        certificate.addTag(new ResourceTag("apigateway:domainName", domainName));
        if (endpointType != null) certificate.addTag(new ResourceTag("apigateway:endpointType", endpointType));
        if (tags != null) tags.forEach((key, value) -> certificate.addTag(new ResourceTag(key, value)));
    }
}
