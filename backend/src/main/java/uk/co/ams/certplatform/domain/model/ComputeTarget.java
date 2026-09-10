package uk.co.ams.certplatform.domain.model;

import uk.co.ams.certplatform.domain.enums.OsFamily;

import java.util.ArrayList;
import java.util.List;

/**
 * A machine the platform can run a certificate scan on - an EC2 instance, an ECS
 * task's host, a VM. Provider-neutral on purpose: the SSM adapter and any future
 * SSH or Azure Run Command adapter all produce these.
 */
public class ComputeTarget {

    private String id;
    private String name;
    private String service;
    private String resourceArn;
    private OsFamily osFamily = OsFamily.UNKNOWN;
    private OperatingSystemMetadata os = new OperatingSystemMetadata();
    private final List<ResourceTag> tags = new ArrayList<>();

    public ComputeTarget() {}

    public ComputeTarget(String id, OsFamily osFamily) {
        this.id = id;
        this.osFamily = osFamily;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getService() { return service; }
    public void setService(String service) { this.service = service; }

    public String getResourceArn() { return resourceArn; }
    public void setResourceArn(String resourceArn) { this.resourceArn = resourceArn; }

    public OsFamily getOsFamily() { return osFamily; }
    public void setOsFamily(OsFamily osFamily) { this.osFamily = osFamily; }

    public OperatingSystemMetadata getOs() { return os; }
    public void setOs(OperatingSystemMetadata os) { this.os = os; }

    public List<ResourceTag> getTags() { return tags; }
    public void addTag(ResourceTag tag) { if (tag != null) tags.add(tag); }

    /** Falls back to the instance id so a usage record always has something readable. */
    public String displayName() {
        return name != null && !name.isBlank() ? name : id;
    }
}
