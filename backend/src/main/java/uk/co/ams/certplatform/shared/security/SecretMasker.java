package uk.co.ams.certplatform.shared.security;

/**
 * Renders a credential safe for API responses and logs: only the last four
 * characters survive.
 */
public final class SecretMasker {

    private SecretMasker() {}

    public static String mask(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() <= 4) return "****";
        return "****" + trimmed.substring(trimmed.length() - 4);
    }
}
