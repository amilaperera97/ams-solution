package uk.co.ams.certplatform.domain.model;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.enums.ServiceScope;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Everything the platform knows about one scannable service, declared by the
 * strategy that implements it.
 *
 * <p>This is the single source of truth for a service: the planner uses
 * {@code scope} and {@code homeRegion} to decide where to run it, the registry
 * uses {@code key} and {@code aliases} to match what the caller asked for, the
 * capabilities API uses {@code label}, {@code phase} and {@code implemented} to
 * render the picker, and {@code requiredPermissions} documents the IAM policy an
 * operator has to grant. Onboarding a service means writing one strategy class
 * that returns one of these - nothing else in the platform has to change.
 *
 * @param provider            cloud the service belongs to
 * @param key                 canonical, stable identifier persisted on scans and certificates
 * @param label               human-readable name for the UI
 * @param phase               rollout phase (1, 2 or 3) - purely informational, used to group the picker
 * @param scope               REGIONAL or GLOBAL
 * @param homeRegion          region GLOBAL services are addressed in; ignored when REGIONAL
 * @param aliases             alternative spellings accepted from API callers and older saved scans
 * @param requiredPermissions provider permissions the strategy needs, for documentation and error hints
 * @param implemented         false for a registered-but-not-yet-built service
 */
public record DiscoveryServiceDescriptor(
        CloudProviderType provider,
        String key,
        String label,
        int phase,
        ServiceScope scope,
        String homeRegion,
        Set<String> aliases,
        List<String> requiredPermissions,
        boolean implemented
) {

    public DiscoveryServiceDescriptor {
        if (provider == null) throw new IllegalArgumentException("provider is required");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("key is required");
        if (scope == ServiceScope.GLOBAL && (homeRegion == null || homeRegion.isBlank())) {
            throw new IllegalArgumentException("GLOBAL service " + key + " must declare a home region");
        }
        key = key.toUpperCase(Locale.ROOT);
        aliases = aliases == null ? Set.of() : normalise(aliases);
        requiredPermissions = requiredPermissions == null ? List.of() : List.copyOf(requiredPermissions);
    }

    private static Set<String> normalise(Set<String> values) {
        Set<String> upper = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) upper.add(value.toUpperCase(Locale.ROOT));
        }
        return Set.copyOf(upper);
    }

    /** True when {@code requested} names this service, by canonical key or any alias. */
    public boolean matches(String requested) {
        if (requested == null || requested.isBlank()) return false;
        String candidate = requested.trim().toUpperCase(Locale.ROOT);
        return key.equals(candidate) || aliases.contains(candidate);
    }

    public boolean isGlobal() {
        return scope == ServiceScope.GLOBAL;
    }

    /** Unique across providers, so it can key a map or appear in a log line unambiguously. */
    public String qualifiedKey() {
        return provider.name() + ":" + key;
    }

    /** Starting point for a descriptor; see {@link Builder} for the optional parts. */
    public static Builder builder(CloudProviderType provider, String key, String label) {
        return new Builder(provider, key, label);
    }

    public static final class Builder {
        private final CloudProviderType provider;
        private final String key;
        private final String label;
        private int phase = 1;
        private ServiceScope scope = ServiceScope.REGIONAL;
        private String homeRegion;
        private Set<String> aliases = Set.of();
        private List<String> requiredPermissions = List.of();
        private boolean implemented = true;

        private Builder(CloudProviderType provider, String key, String label) {
            this.provider = provider;
            this.key = key;
            this.label = label;
        }

        public Builder phase(int phase) { this.phase = phase; return this; }

        /** Marks the service global and pins it to the region its API lives in. */
        public Builder global(String homeRegion) {
            this.scope = ServiceScope.GLOBAL;
            this.homeRegion = homeRegion;
            return this;
        }

        public Builder aliases(String... aliases) { this.aliases = Set.of(aliases); return this; }

        public Builder requiredPermissions(String... permissions) {
            this.requiredPermissions = List.of(permissions);
            return this;
        }

        /** Call on a registered service whose live implementation has not been written yet. */
        public Builder notImplemented() { this.implemented = false; return this; }

        public DiscoveryServiceDescriptor build() {
            return new DiscoveryServiceDescriptor(provider, key, label, phase, scope, homeRegion,
                    aliases, requiredPermissions, implemented);
        }
    }
}
