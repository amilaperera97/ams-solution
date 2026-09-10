package uk.co.ams.certplatform.domain.enums;

import java.util.Locale;

/** Operating system family of a compute target, used to pick the right scan script. */
public enum OsFamily {
    LINUX,
    WINDOWS,
    MACOS,
    UNKNOWN;

    /** Maps whatever the provider reports ("Linux", "windows", "Amazon Linux") onto a family. */
    public static OsFamily from(String reported) {
        if (reported == null || reported.isBlank()) return UNKNOWN;
        String value = reported.toLowerCase(Locale.ROOT);
        if (value.contains("windows")) return WINDOWS;
        if (value.contains("mac") || value.contains("darwin")) return MACOS;
        if (value.contains("linux") || value.contains("unix") || value.contains("ubuntu")
                || value.contains("amazon") || value.contains("rhel") || value.contains("centos")
                || value.contains("debian") || value.contains("suse")) return LINUX;
        return UNKNOWN;
    }
}
