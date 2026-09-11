package uk.co.ams.certplatform.api.controller;

import java.util.Map;

/** Headline counts for the dashboard landing page. */
public record DashboardStatsResponse(
        long totalProviders,
        long totalEnvironments,
        long totalAccounts,
        long totalCertificates,
        Map<String, Long> certificateHealth
) {
}
