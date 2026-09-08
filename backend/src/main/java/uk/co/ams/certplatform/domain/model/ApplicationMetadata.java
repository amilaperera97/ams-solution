package uk.co.ams.certplatform.domain.model;

public class ApplicationMetadata {
    private String applicationName;
    private String runtime;
    private String runtimeVersion;
    private String framework;
    private String frameworkVersion;
    private String processName;
    private String deploymentType;

    public ApplicationMetadata() {}

    public String getApplicationName() { return applicationName; }
    public void setApplicationName(String applicationName) { this.applicationName = applicationName; }

    public String getRuntime() { return runtime; }
    public void setRuntime(String runtime) { this.runtime = runtime; }

    public String getRuntimeVersion() { return runtimeVersion; }
    public void setRuntimeVersion(String runtimeVersion) { this.runtimeVersion = runtimeVersion; }

    public String getFramework() { return framework; }
    public void setFramework(String framework) { this.framework = framework; }

    public String getFrameworkVersion() { return frameworkVersion; }
    public void setFrameworkVersion(String frameworkVersion) { this.frameworkVersion = frameworkVersion; }

    public String getProcessName() { return processName; }
    public void setProcessName(String processName) { this.processName = processName; }

    public String getDeploymentType() { return deploymentType; }
    public void setDeploymentType(String deploymentType) { this.deploymentType = deploymentType; }
}
