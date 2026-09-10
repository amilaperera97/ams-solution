package uk.co.ams.certplatform.domain.model;

import uk.co.ams.certplatform.domain.enums.DiscoveryStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What one discovery task found. Carries its own provenance so a result can be
 * attributed to an account, region and service after the fan-out has collapsed.
 */
public class DiscoveryResult {

    private final List<Certificate> certificates = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    private DiscoveryStatus status = DiscoveryStatus.SUCCESS;
    private String message;

    private String provider;
    private String accountId;
    private String region;
    private String service;

    public DiscoveryResult() {}

    public DiscoveryResult(String provider, String accountId, String region, String service) {
        this.provider = provider;
        this.accountId = accountId;
        this.region = region;
        this.service = service;
    }

    public static DiscoveryResult forTask(DiscoveryTask task) {
        return new DiscoveryResult(
                task.descriptor().provider().name(),
                task.account().getId(),
                task.region(),
                task.descriptor().key());
    }

    public List<Certificate> getCertificates() { return Collections.unmodifiableList(certificates); }
    public void addCertificate(Certificate certificate) { if (certificate != null) certificates.add(certificate); }
    public void addCertificates(List<Certificate> found) { if (found != null) found.forEach(this::addCertificate); }

    public DiscoveryStatus getStatus() { return status; }
    public void setStatus(DiscoveryStatus status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<String> getErrors() { return Collections.unmodifiableList(errors); }
    public void addError(String error) { if (error != null) errors.add(error); }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    // --- terminal-state helpers, so strategies do not repeat status+message pairs ---

    public DiscoveryResult succeeded(String message) {
        this.status = DiscoveryStatus.SUCCESS;
        this.message = message;
        return this;
    }

    /** Some resources were read and some were not; the scan is still worth keeping. */
    public DiscoveryResult partial(String message) {
        this.status = DiscoveryStatus.PARTIAL;
        this.message = message;
        return this;
    }

    public DiscoveryResult failed(String message) {
        this.status = DiscoveryStatus.FAILED;
        this.message = message;
        addError(message);
        return this;
    }

    public DiscoveryResult skipped(String message) {
        this.status = DiscoveryStatus.SKIPPED;
        this.message = message;
        return this;
    }

    public DiscoveryResult notImplemented(String message) {
        this.status = DiscoveryStatus.NOT_IMPLEMENTED;
        this.message = message;
        return this;
    }

    /**
     * Downgrades SUCCESS to PARTIAL once at least one resource-level error has been
     * recorded, so a strategy can keep going past a single unreadable resource and
     * still report honestly at the end.
     */
    public DiscoveryResult settle(String successMessage) {
        if (errors.isEmpty()) return succeeded(successMessage);
        return partial(successMessage + " (" + errors.size() + " resource(s) could not be read)");
    }
}
