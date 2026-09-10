package uk.co.ams.certplatform.domain.model;

import uk.co.ams.certplatform.domain.enums.OsFamily;

import java.time.Duration;
import java.util.List;

/**
 * A script to run on a compute target.
 *
 * @param osFamily which family the script is written for
 * @param lines    the script, one element per line
 * @param timeout  how long the executor should wait before giving up on the target
 */
public record ComputeCommand(OsFamily osFamily, List<String> lines, Duration timeout) {

    public ComputeCommand {
        lines = List.copyOf(lines);
    }

    public static ComputeCommand of(OsFamily osFamily, List<String> lines) {
        return new ComputeCommand(osFamily, lines, Duration.ofMinutes(2));
    }
}
