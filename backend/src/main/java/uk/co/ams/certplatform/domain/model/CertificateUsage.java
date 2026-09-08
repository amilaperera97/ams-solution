package com.example.certplatform.domain.model;

public class CertificateUsage {
    private String service;
    private String resource;
    private String resourceType;
    private String usageType;
    private String region;
    private String account;
    private String environment;
    private ApplicationMetadata application;
    private OperatingSystemMetadata os;

    public CertificateUsage() {}

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getResource() { return resource; }
    public void setResource(String resource) { this.resource = resource; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getUsageType() { return usageType; }
    public void setUsageType(String usageType) { this.usageType = usageType; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getAccount() { return account; }
    public void setAccount(String account) { this.account = account; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public ApplicationMetadata getApplication() { return application; }
    public void setApplication(ApplicationMetadata application) { this.application = application; }

    public OperatingSystemMetadata getOs() { return os; }
    public void setOs(OperatingSystemMetadata os) { this.os = os; }
}
