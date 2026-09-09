package uk.co.ams.certplatform.api.controller;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.shared.config.CloudProviderProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Read-only view of the {@code cloud.providers.*} configuration.
 *
 * The UI needs it to know which auth types make sense: TOKEN can never reach real
 * AWS, so the account form hides it once AWS is in REAL mode rather than letting an
 * operator fill in a form the backend is bound to reject. Nothing secret is exposed -
 * the endpoint override is reported as a boolean, not a URL.
 */
@RestController
@RequestMapping("/api/v1/config")
public class CloudProviderConfigController {

    private final CloudProviderProperties properties;

    public CloudProviderConfigController(CloudProviderProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/cloud-providers")
    public ResponseEntity<Map<String, CloudProviderConfigResponse>> cloudProviders() {
        Map<String, CloudProviderConfigResponse> byType = new LinkedHashMap<>();
        for (CloudProviderType type : CloudProviderType.values()) {
            CloudProviderProperties.ProviderSettings settings = properties.settingsFor(type);
            byType.put(type.name(), new CloudProviderConfigResponse(
                    properties.modeFor(type).name(),
                    settings.getDefaultRegion(),
                    settings.getEndpoint() != null && !settings.getEndpoint().isBlank()));
        }
        return ResponseEntity.ok(byType);
    }

    public record CloudProviderConfigResponse(String mode, String defaultRegion, boolean endpointOverridden) {}
}
