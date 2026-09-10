package uk.co.ams.certplatform.application.port;

import uk.co.ams.certplatform.domain.enums.CloudProviderType;
import uk.co.ams.certplatform.domain.model.ComputeCommand;
import uk.co.ams.certplatform.domain.model.ComputeCommandResult;
import uk.co.ams.certplatform.domain.model.ComputeTarget;
import uk.co.ams.certplatform.domain.model.ScanContext;

import java.util.List;

/**
 * Runs a command on the machines inside an account, so certificates sitting on a
 * filesystem can be read without the platform holding SSH keys or opening ports.
 *
 * <p>The AWS implementation uses SSM Run Command. An Azure implementation would
 * use Run Command on VMs, a GCP one the OS Config agent; a self-hosted estate
 * could supply an SSH adapter. Discovery strategies depend only on this port, so
 * every compute-side service works on any provider that supplies an adapter.
 */
public interface ComputeExecutor {

    /** Which cloud this adapter can reach. */
    boolean supports(CloudProviderType provider);

    /**
     * Machines in scope for this account and region that the adapter can actually
     * reach. Unreachable instances are left out rather than reported as failures -
     * an instance without the agent installed is a coverage gap, not an error.
     */
    List<ComputeTarget> listTargets(ScanContext context);

    /**
     * Runs one command across many targets. Batched because the underlying
     * transports charge per invocation and rate-limit accordingly: sending one
     * command to fifty instances is one API call, sending fifty is fifty.
     *
     * <p>Returns one result per requested target, in the same order, including
     * failures - the caller should never have to correlate by index.
     */
    List<ComputeCommandResult> run(ScanContext context, List<ComputeTarget> targets, ComputeCommand command);
}
