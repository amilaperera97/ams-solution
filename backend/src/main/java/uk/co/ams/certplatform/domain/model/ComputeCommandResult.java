package uk.co.ams.certplatform.domain.model;

/**
 * Output of running a {@link ComputeCommand} on one target.
 *
 * @param target    the machine the command ran on
 * @param succeeded true when the command completed with a zero exit status
 * @param stdout    standard output, possibly truncated by the transport
 * @param stderr    standard error, used to explain a failure
 * @param truncated true when the transport cut the output short, so callers know
 *                  the certificate list may be incomplete rather than empty
 */
public record ComputeCommandResult(
        ComputeTarget target,
        boolean succeeded,
        String stdout,
        String stderr,
        boolean truncated
) {

    public static ComputeCommandResult failure(ComputeTarget target, String reason) {
        return new ComputeCommandResult(target, false, "", reason, false);
    }
}
