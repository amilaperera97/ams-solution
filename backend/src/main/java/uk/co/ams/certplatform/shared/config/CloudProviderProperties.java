package uk.co.ams.certplatform.shared.config;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.enums.ProviderMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Binds the {@code cloud.providers.*} block. Until now that block existed in
 * application-dev.yml but nothing read it, so MOCK/REAL had no effect.
 */
@ConfigurationProperties(prefix = "cloud")
public class CloudProviderProperties {

    private Map<String, ProviderSettings> providers = new LinkedHashMap<>();

    public Map<String, ProviderSettings> getProviders() { return providers; }
    public void setProviders(Map<String, ProviderSettings> providers) { this.providers = providers; }

    public ProviderSettings settingsFor(CloudProviderType type) {
        if (type == null || providers == null) return new ProviderSettings();
        ProviderSettings settings = providers.get(type.name().toLowerCase(Locale.ROOT));
        if (settings == null) settings = providers.get(type.name());
        return settings != null ? settings : new ProviderSettings();
    }

    public ProviderMode modeFor(CloudProviderType type) {
        ProviderMode mode = settingsFor(type).getMode();
        return mode != null ? mode : ProviderMode.MOCK;
    }

    public boolean isReal(CloudProviderType type) {
        return modeFor(type) == ProviderMode.REAL;
    }

    public static class ProviderSettings {
        /** MOCK (simulated results) or REAL (live cloud API calls). */
        private ProviderMode mode = ProviderMode.MOCK;
        /** Optional endpoint override - the wiremock stub in dev, or LocalStack in qa. */
        private String endpoint;
        /** Region used when an account has none of its own. */
        private String defaultRegion = "us-east-1";
        /** Per-API-call timeout for live SDK calls, in seconds. */
        private int apiTimeoutSeconds = 30;

        public ProviderMode getMode() { return mode; }
        public void setMode(ProviderMode mode) { this.mode = mode; }

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

        public String getDefaultRegion() { return defaultRegion; }
        public void setDefaultRegion(String defaultRegion) { this.defaultRegion = defaultRegion; }

        public int getApiTimeoutSeconds() { return apiTimeoutSeconds; }
        public void setApiTimeoutSeconds(int apiTimeoutSeconds) { this.apiTimeoutSeconds = apiTimeoutSeconds; }
    }
}
