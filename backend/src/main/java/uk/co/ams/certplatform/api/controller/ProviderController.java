package com.example.certplatform.api.controller;

import com.example.certplatform.application.service.ProviderService;
import com.example.certplatform.domain.model.Provider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ProviderController {

    private final ProviderService providerService;

    public ProviderController(ProviderService providerService) {
        this.providerService = providerService;
    }

    @PostMapping("/organisations/{organisationId}/providers")
    public ResponseEntity<Provider> createProvider(@PathVariable String organisationId, @RequestBody CreateProviderRequest request) {
        Provider created = providerService.createProvider(organisationId, request.name(), request.type());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/organisations/{organisationId}/providers")
    public ResponseEntity<List<Provider>> getProvidersByOrganisation(@PathVariable String organisationId) {
        List<Provider> providers = providerService.getProvidersByOrganisationId(organisationId);
        return ResponseEntity.ok(providers);
    }

    @GetMapping("/providers/{id}")
    public ResponseEntity<Provider> getProvider(@PathVariable String id) {
        return providerService.getProvider(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
