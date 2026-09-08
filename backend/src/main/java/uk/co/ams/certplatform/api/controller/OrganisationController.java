package uk.co.ams.certplatform.api.controller;

import uk.co.ams.certplatform.application.service.OrganisationService;
import uk.co.ams.certplatform.domain.model.Organisation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/organisations")
public class OrganisationController {

    private final OrganisationService organisationService;

    public OrganisationController(OrganisationService organisationService) {
        this.organisationService = organisationService;
    }

    @PostMapping
    public ResponseEntity<Organisation> createOrganisation(@RequestBody CreateOrganisationRequest request) {
        Organisation created = organisationService.createOrganisation(request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/current")
    public ResponseEntity<Organisation> getCurrentOrganisation() {
        Optional<Organisation> org = organisationService.getCurrentOrganisation();
        return org.map(ResponseEntity::ok)
                  .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Organisation> getOrganisation(@PathVariable String id) {
        Optional<Organisation> org = organisationService.getOrganisation(id);
        return org.map(ResponseEntity::ok)
                  .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
