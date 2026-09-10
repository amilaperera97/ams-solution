package uk.co.ams.certplatform.infrastructure.compute.source;

import java.util.ArrayList;
import java.util.List;

/**
 * The wire format every compute scan script writes: one line per certificate,
 * {@code CERT|<location>|<base64 DER>}.
 *
 * <p>Line-oriented rather than JSON so the scripts need nothing beyond a shell -
 * no jq, no python, no assumptions about what is installed on a hardened AMI.
 * Anything a script writes that is not a CERT line (warnings, shell noise) is
 * ignored, which keeps parsing robust against chatty environments.
 */
public final class ComputeOutput {

    public static final String PREFIX = "CERT|";

    private ComputeOutput() {}

    /** One certificate as reported by a script. */
    public record Record(String location, String base64Der) {}

    public static List<Record> parse(String stdout) {
        List<Record> records = new ArrayList<>();
        if (stdout == null || stdout.isBlank()) return records;

        for (String line : stdout.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith(PREFIX)) continue;
            String[] parts = trimmed.substring(PREFIX.length()).split("\\|", 2);
            if (parts.length == 2 && !parts[1].isBlank()) {
                records.add(new Record(parts[0], parts[1]));
            }
        }
        return records;
    }
}
