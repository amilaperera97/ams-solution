package uk.co.ams.certplatform.shared.config;

import uk.co.ams.certplatform.domain.model.DiscoveryOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Binds the {@code certificate-discovery.*} block: how hard to push the cloud
 * APIs, how invasive discovery is allowed to be, and which services to turn off.
 */
@ConfigurationProperties(prefix = "certificate-discovery")
public class DiscoveryProperties {

    private Parallelism parallelism = new Parallelism();
    private Options options = new Options();
    private Compute compute = new Compute();

    /**
     * Qualified keys ({@code AWS:S3}) or bare keys ({@code S3}) to exclude from
     * every scan. Lets an operator switch off an expensive or sensitive service
     * without redeploying, and keeps a misbehaving integration from blocking the rest.
     */
    private List<String> disabledServices = new ArrayList<>();

    public Parallelism getParallelism() { return parallelism; }
    public void setParallelism(Parallelism parallelism) { this.parallelism = parallelism; }

    public Options getOptions() { return options; }
    public void setOptions(Options options) { this.options = options; }

    public Compute getCompute() { return compute; }
    public void setCompute(Compute compute) { this.compute = compute; }

    public List<String> getDisabledServices() { return disabledServices; }
    public void setDisabledServices(List<String> disabledServices) { this.disabledServices = disabledServices; }

    public boolean isDisabled(String qualifiedKey, String key) {
        if (disabledServices == null || disabledServices.isEmpty()) return false;
        Set<String> normalised = new LinkedHashSet<>();
        disabledServices.forEach(s -> { if (s != null) normalised.add(s.trim().toUpperCase(Locale.ROOT)); });
        return normalised.contains(qualifiedKey.toUpperCase(Locale.ROOT))
                || normalised.contains(key.toUpperCase(Locale.ROOT));
    }

    public DiscoveryOptions toDiscoveryOptions() {
        return new DiscoveryOptions(options.readSecretContent, options.scanComputeFilesystems,
                options.maxResourcesPerService);
    }

    public static class Parallelism {
        /** Upper bound on discovery tasks in flight across the whole application. */
        private int maxConcurrentTasks = 20;
        /** Upper bound per account, so one large account cannot starve the others. */
        private int maxConcurrentTasksPerAccount = 8;
        /** Task starts allowed per second per account; the guard against API throttling. */
        private double taskStartsPerSecondPerAccount = 10.0;
        /** Hard stop for a whole scan, in minutes. */
        private int scanTimeoutMinutes = 60;

        public int getMaxConcurrentTasks() { return maxConcurrentTasks; }
        public void setMaxConcurrentTasks(int v) { this.maxConcurrentTasks = v; }

        public int getMaxConcurrentTasksPerAccount() { return maxConcurrentTasksPerAccount; }
        public void setMaxConcurrentTasksPerAccount(int v) { this.maxConcurrentTasksPerAccount = v; }

        public double getTaskStartsPerSecondPerAccount() { return taskStartsPerSecondPerAccount; }
        public void setTaskStartsPerSecondPerAccount(double v) { this.taskStartsPerSecondPerAccount = v; }

        public int getScanTimeoutMinutes() { return scanTimeoutMinutes; }
        public void setScanTimeoutMinutes(int v) { this.scanTimeoutMinutes = v; }
    }

    public static class Options {
        /** Off by default: listing secrets is safe, downloading every secret value is not. */
        private boolean readSecretContent = false;
        private boolean scanComputeFilesystems = true;
        private int maxResourcesPerService = 5000;

        public boolean isReadSecretContent() { return readSecretContent; }
        public void setReadSecretContent(boolean v) { this.readSecretContent = v; }

        public boolean isScanComputeFilesystems() { return scanComputeFilesystems; }
        public void setScanComputeFilesystems(boolean v) { this.scanComputeFilesystems = v; }

        public int getMaxResourcesPerService() { return maxResourcesPerService; }
        public void setMaxResourcesPerService(int v) { this.maxResourcesPerService = v; }
    }

    public static class Compute {
        /**
         * Directories searched for certificate files on Linux targets. Deliberately
         * excludes {@code /etc/ssl/certs}: that is the OS trust bundle, hundreds of
         * public CA certificates that are noise here and would swamp the output limit.
         */
        private List<String> linuxPaths = new ArrayList<>(List.of(
                "/etc/pki/tls/private", "/etc/nginx", "/etc/httpd/conf.d", "/etc/apache2",
                "/etc/haproxy", "/etc/ssl/private", "/opt/ssl", "/opt/certs", "/srv/ssl"));
        /** Windows certificate stores enumerated on Windows targets. */
        private List<String> windowsStores = new ArrayList<>(List.of(
                "Cert:\\LocalMachine\\My", "Cert:\\LocalMachine\\WebHosting"));
        /** Directories searched for Java keystores on any platform. */
        private List<String> keystorePaths = new ArrayList<>(List.of(
                "/opt", "/srv", "/usr/local", "/etc"));
        /** Passwords tried against discovered keystores, in order. Blank means "try the JDK default". */
        private List<String> keystorePasswords = new ArrayList<>(List.of("changeit"));
        /** Per-target command timeout. */
        private int commandTimeoutSeconds = 120;
        /** Most instances to touch per account and region; protects against a 10,000-instance estate. */
        private int maxTargetsPerRegion = 200;
        /** Instances scanned at once within a region. */
        private int targetConcurrency = 10;

        public List<String> getLinuxPaths() { return linuxPaths; }
        public void setLinuxPaths(List<String> v) { this.linuxPaths = v; }

        public List<String> getWindowsStores() { return windowsStores; }
        public void setWindowsStores(List<String> v) { this.windowsStores = v; }

        public List<String> getKeystorePaths() { return keystorePaths; }
        public void setKeystorePaths(List<String> v) { this.keystorePaths = v; }

        public List<String> getKeystorePasswords() { return keystorePasswords; }
        public void setKeystorePasswords(List<String> v) { this.keystorePasswords = v; }

        public int getCommandTimeoutSeconds() { return commandTimeoutSeconds; }
        public void setCommandTimeoutSeconds(int v) { this.commandTimeoutSeconds = v; }

        public int getMaxTargetsPerRegion() { return maxTargetsPerRegion; }
        public void setMaxTargetsPerRegion(int v) { this.maxTargetsPerRegion = v; }

        public int getTargetConcurrency() { return targetConcurrency; }
        public void setTargetConcurrency(int v) { this.targetConcurrency = v; }
    }
}
