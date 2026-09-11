package uk.co.ams.certplatform.application.service;

import uk.co.ams.certplatform.application.port.CertificateDiscoveryStrategy;
import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.Account;
import uk.co.ams.certplatform.domain.model.DiscoveryServiceDescriptor;
import uk.co.ams.certplatform.domain.model.DiscoveryTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DiscoveryTaskPlannerTest {

    private DiscoveryTaskPlanner planner;
    private Account account;

    @BeforeEach
    void setUp() {
        CertificateDiscoveryStrategyRegistry registry = new CertificateDiscoveryStrategyRegistry(List.of(
                strategy(DiscoveryServiceDescriptor.builder(CloudProviderType.AWS, "ACM", "ACM").build()),
                strategy(DiscoveryServiceDescriptor.builder(CloudProviderType.AWS, "ALB", "ALB")
                        .aliases("APPLICATION_LOAD_BALANCER").build()),
                strategy(DiscoveryServiceDescriptor.builder(CloudProviderType.AWS, "CLOUDFRONT", "CloudFront")
                        .global("us-east-1").build()),
                strategy(DiscoveryServiceDescriptor.builder(CloudProviderType.AWS, "S3", "S3")
                        .phase(2).notImplemented().build())));
        planner = new DiscoveryTaskPlanner(registry);

        account = Account.builder().id("acc-1").region("eu-west-2").build();
    }

    @Test
    void expandsRegionalServicesAcrossEveryRequestedRegion() {
        DiscoveryTaskPlanner.Plan plan = planner.plan(List.of(target()),
                List.of("eu-west-1", "eu-west-2"), List.of("ACM"));

        assertEquals(2, plan.tasks().size());
        assertEquals(List.of("eu-west-1", "eu-west-2"), plan.tasks().stream().map(DiscoveryTask::region).toList());
    }

    @Test
    void schedulesAGlobalServiceOnceRegardlessOfHowManyRegionsWereAsked() {
        DiscoveryTaskPlanner.Plan plan = planner.plan(List.of(target()),
                List.of("eu-west-1", "eu-west-2", "us-east-1"), List.of("CLOUDFRONT"));

        assertEquals(1, plan.tasks().size(), "a global service listed per region would triple-count every certificate");
        assertEquals("us-east-1", plan.tasks().get(0).region());
    }

    @Test
    void scansEveryImplementedServiceWhenNoneWereNamed() {
        DiscoveryTaskPlanner.Plan plan = planner.plan(List.of(target()), List.of("eu-west-2"), List.of());

        List<String> services = plan.tasks().stream().map(t -> t.descriptor().key()).toList();
        assertEquals(List.of("ACM", "ALB", "CLOUDFRONT"), services);
        assertFalse(services.contains("S3"), "an unbuilt service must not be scheduled implicitly");
    }

    @Test
    void acceptsAnAliasForAServiceName() {
        DiscoveryTaskPlanner.Plan plan = planner.plan(List.of(target()),
                List.of("eu-west-2"), List.of("APPLICATION_LOAD_BALANCER"));

        assertEquals(1, plan.tasks().size());
        assertEquals("ALB", plan.tasks().get(0).descriptor().key());
    }

    @Test
    void reportsAServiceNameNoStrategyClaimsInsteadOfDroppingIt() {
        DiscoveryTaskPlanner.Plan plan = planner.plan(List.of(target()),
                List.of("eu-west-2"), List.of("ACM", "DYNAMODB"));

        assertEquals(1, plan.tasks().size());
        assertEquals(List.of("AWS:DYNAMODB"), plan.unknownServices());
    }

    @Test
    void ignoresARepeatedServiceName() {
        DiscoveryTaskPlanner.Plan plan = planner.plan(List.of(target()),
                List.of("eu-west-2"), List.of("ALB", "APPLICATION_LOAD_BALANCER", "alb"));

        assertEquals(1, plan.tasks().size());
    }

    @Test
    void fallsBackToTheAccountHomeRegionWhenNoneWereRequested() {
        DiscoveryTaskPlanner.Plan plan = planner.plan(List.of(target()), List.of(), List.of("ACM"));

        assertEquals(1, plan.tasks().size());
        assertEquals("eu-west-2", plan.tasks().get(0).region());
    }

    @Test
    void treatsTheLiteralPlaceholderRegionAsNoRegion() {
        // Older saved scans carry "default"; passing that to the SDK would be a hard failure.
        DiscoveryTaskPlanner.Plan plan = planner.plan(List.of(target()), List.of("default"), List.of("ACM"));

        assertEquals("eu-west-2", plan.tasks().get(0).region());
    }

    @Test
    void leavesTheRegionUnsetWhenNeitherTheScanNorTheAccountNamesOne() {
        Account regionless = Account.builder().id("acc-2").build();

        DiscoveryTaskPlanner.Plan plan = planner.plan(
                List.of(new DiscoveryTaskPlanner.AccountTarget(regionless, CloudProviderType.AWS)),
                List.of(), List.of("ACM"));

        // The client factory substitutes the configured default region.
        assertNull(plan.tasks().get(0).region());
    }

    @Test
    void planssNothingForAProviderWithNoStrategies() {
        DiscoveryTaskPlanner.Plan plan = planner.plan(
                List.of(new DiscoveryTaskPlanner.AccountTarget(account, CloudProviderType.GCP)),
                List.of("europe-west2"), List.of());

        assertTrue(plan.tasks().isEmpty());
    }

    private DiscoveryTaskPlanner.AccountTarget target() {
        return new DiscoveryTaskPlanner.AccountTarget(account, CloudProviderType.AWS);
    }

    private static CertificateDiscoveryStrategy strategy(DiscoveryServiceDescriptor descriptor) {
        CertificateDiscoveryStrategy mock = mock(CertificateDiscoveryStrategy.class);
        when(mock.descriptor()).thenReturn(descriptor);
        return mock;
    }
}
