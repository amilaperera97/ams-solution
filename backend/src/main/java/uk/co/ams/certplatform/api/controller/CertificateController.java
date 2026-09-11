package uk.co.ams.certplatform.api.controller;

import uk.co.ams.certplatform.application.service.CertificateService;
import uk.co.ams.certplatform.domain.model.Certificate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class CertificateController {

    private final CertificateService certificateService;

    public CertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @GetMapping("/scans/{scanId}/certificates")
    public ResponseEntity<List<Certificate>> getCertificatesByScan(@PathVariable String scanId) {
        List<Certificate> certificates = certificateService.getCertificatesByScanId(scanId);
        return ResponseEntity.ok(certificates);
    }

    @GetMapping("/certificates/{id}")
    public ResponseEntity<Certificate> getCertificate(@PathVariable String id) {
        return certificateService.getCertificateById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/scans/{scanId}/certificates/export", produces = "text/csv")
    public ResponseEntity<String> exportCertificatesCsv(@PathVariable String scanId) {
        List<Certificate> certificates = certificateService.getCertificatesByScanId(scanId);
        
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Domain,Provider,AccountID,Environment,Region,Status,IssuedDate,ExpiryDate,Issuer,Algorithm,AutoRenewal\n");
        
        for (Certificate cert : certificates) {
            csv.append(cert.id()).append(",")
               .append(cert.domain()).append(",")
               .append(cert.provider()).append(",")
               .append(cert.accountId()).append(",")
               .append(cert.environment()).append(",")
               .append(cert.region()).append(",")
               .append(cert.status()).append(",")
               .append(cert.issuedDate()).append(",")
               .append(cert.expiryDate()).append(",")
               .append(cert.issuer()).append(",")
               .append(cert.algorithm()).append(",")
               .append(cert.autoRenewal()).append("\n");
        }
        
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"scan-" + scanId + "-certificates.csv\"")
                .body(csv.toString());
    }
}
