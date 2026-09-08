package uk.co.ams.certplatform.api.controller;

import uk.co.ams.certplatform.application.service.ScanService;
import uk.co.ams.certplatform.domain.model.Scan;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/scans")
public class ScanController {

    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    @PostMapping
    public ResponseEntity<?> createScan(@RequestBody CreateScanRequest request) {
        Scan scan = scanService.createScan(
            request.name(),
            request.scopeType(),
            request.providerIds(),
            request.environmentIds(),
            request.accountIds(),
            request.regions(),
            request.services()
        );
        return ResponseEntity.accepted().body(scan);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Scan> getScan(@PathVariable String id) {
        return scanService.getScan(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<Scan>> getAllScans() {
        return ResponseEntity.ok(scanService.getAllScans());
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<?> getScanStatus(@PathVariable String id) {
        return scanService.getScan(id)
                .map(scan -> ResponseEntity.ok().body(new ScanStatusResponse(scan)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelScan(@PathVariable String id) {
        return ResponseEntity.ok(scanService.cancelScan(id));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retryScan(@PathVariable String id) {
        return ResponseEntity.accepted().body(scanService.retryScan(id));
    }

    @PostMapping("/{id}/run-again")
    public ResponseEntity<?> runAgain(@PathVariable String id) {
        return ResponseEntity.accepted().body(scanService.runAgain(id));
    }
}
