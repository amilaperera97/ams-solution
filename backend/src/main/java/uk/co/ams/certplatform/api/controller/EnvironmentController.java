package uk.co.ams.certplatform.api.controller;

import uk.co.ams.certplatform.application.service.EnvironmentService;
import uk.co.ams.certplatform.domain.model.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    public EnvironmentController(EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    @PostMapping("/providers/{providerId}/environments")
    public ResponseEntity<Environment> createEnvironment(@PathVariable String providerId, @RequestBody CreateEnvironmentRequest request) {
        Environment created = environmentService.createEnvironment(providerId, request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/providers/{providerId}/environments")
    public ResponseEntity<List<Environment>> getEnvironmentsByProvider(@PathVariable String providerId) {
        List<Environment> environments = environmentService.getEnvironmentsByProviderId(providerId);
        return ResponseEntity.ok(environments);
    }

    @GetMapping("/environments/{id}")
    public ResponseEntity<Environment> getEnvironment(@PathVariable String id) {
        return environmentService.getEnvironment(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
