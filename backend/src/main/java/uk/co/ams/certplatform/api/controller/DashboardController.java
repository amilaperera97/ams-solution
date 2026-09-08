package uk.co.ams.certplatform.api.controller;

import uk.co.ams.certplatform.application.port.AccountRepositoryPort;
import uk.co.ams.certplatform.application.port.CertificateRepositoryPort;
import uk.co.ams.certplatform.application.port.EnvironmentRepositoryPort;
import uk.co.ams.certplatform.application.port.ProviderRepositoryPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final ProviderRepositoryPort providerRepositoryPort;
    private final EnvironmentRepositoryPort environmentRepositoryPort;
    private final AccountRepositoryPort accountRepositoryPort;
    private final CertificateRepositoryPort certificateRepositoryPort;

    public DashboardController(ProviderRepositoryPort providerRepositoryPort,
                               EnvironmentRepositoryPort environmentRepositoryPort,
                               AccountRepositoryPort accountRepositoryPort,
                               CertificateRepositoryPort certificateRepositoryPort) {
        this.providerRepositoryPort = providerRepositoryPort;
        this.environmentRepositoryPort = environmentRepositoryPort;
        this.accountRepositoryPort = accountRepositoryPort;
        this.certificateRepositoryPort = certificateRepositoryPort;
    }

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsResponse> getStats() {
        long totalProviders = providerRepositoryPort.count();
        long totalEnvironments = environmentRepositoryPort.count();
        long totalAccounts = accountRepositoryPort.count();
        long totalCertificates = certificateRepositoryPort.count();

        Map<String, Long> health = new HashMap<>();
        health.put("healthy", certificateRepositoryPort.countByStatus("VALID"));
        health.put("expiringSoon", certificateRepositoryPort.countByStatus("EXPIRING_SOON"));
        health.put("critical", certificateRepositoryPort.countByStatus("CRITICAL"));
        health.put("expired", certificateRepositoryPort.countByStatus("EXPIRED"));

        DashboardStatsResponse response = new DashboardStatsResponse(
                totalProviders,
                totalEnvironments,
                totalAccounts,
                totalCertificates,
                health
        );

        return ResponseEntity.ok(response);
    }
}
