package com.example.certplatform.application.port;

import com.example.certplatform.domain.enums.CloudProviderType;

public class DiscoveryCapability {
    private CloudProviderType provider;
    private String service;
    private String resourceType;
    private String operatingSystemSupport;
    private String runtimeSupport;
    private String capabilityName;

    public DiscoveryCapability(CloudProviderType provider, String service, String capabilityName) {
        this.provider = provider;
        this.service = service;
        this.capabilityName = capabilityName;
    }

    public CloudProviderType getProvider() { return provider; }
    public void setProvider(CloudProviderType provider) { this.provider = provider; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getOperatingSystemSupport() { return operatingSystemSupport; }
    public void setOperatingSystemSupport(String operatingSystemSupport) { this.operatingSystemSupport = operatingSystemSupport; }

    public String getRuntimeSupport() { return runtimeSupport; }
    public void setRuntimeSupport(String runtimeSupport) { this.runtimeSupport = runtimeSupport; }

    public String getCapabilityName() { return capabilityName; }
    public void setCapabilityName(String capabilityName) { this.capabilityName = capabilityName; }
}
