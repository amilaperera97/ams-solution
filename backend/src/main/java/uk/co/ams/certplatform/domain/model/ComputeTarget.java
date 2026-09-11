package uk.co.ams.certplatform.domain.model;

import uk.co.ams.certplatform.domain.enums.OsFamily;

import java.util.ArrayList;
import java.util.List;

/**
 * A machine the platform can run a certificate scan on - an EC2 instance, an ECS
 * task's host, a VM. Provider-neutral on purpose: the SSM adapter and any future
 * SSH or Azure Run Command adapter all produce these.
 *
 * <p>Discovery learns about a target in two passes (SSM names the platform, EC2
 * names the tags), so the {@code with*} methods exist to fold the second pass in
 * without giving anyone a mutable target to hold on to.
 */
public record ComputeTarget(
        String id,
        String name,
        String service,
        String resourceArn,
        OsFamily osFamily,
        OperatingSystemMetadata os,
        List<ResourceTag> tags
) {

    public ComputeTarget {
        osFamily = osFamily != null ? osFamily : OsFamily.UNKNOWN;
        os = os != null ? os : OperatingSystemMetadata.unknown();
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    /** Falls back to the instance id so a usage record always has something readable. */
    public String displayName() {
        return name != null && !name.isBlank() ? name : id;
    }

    public ComputeTarget withName(String name) {
        return new ComputeTarget(id, name, service, resourceArn, osFamily, os, tags);
    }

    public ComputeTarget withResourceArn(String resourceArn) {
        return new ComputeTarget(id, name, service, resourceArn, osFamily, os, tags);
    }

    /** Returns a copy carrying one more tag; a null tag is ignored. */
    public ComputeTarget withTag(ResourceTag tag) {
        if (tag == null) return this;
        List<ResourceTag> combined = new ArrayList<>(tags);
        combined.add(tag);
        return new ComputeTarget(id, name, service, resourceArn, osFamily, os, combined);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public static final class Builder {
        private final String id;
        private String name;
        private String service;
        private String resourceArn;
        private OsFamily osFamily = OsFamily.UNKNOWN;
        private OperatingSystemMetadata os = OperatingSystemMetadata.unknown();
        private final List<ResourceTag> tags = new ArrayList<>();

        private Builder(String id) {
            this.id = id;
        }

        public Builder name(String name) { this.name = name; return this; }
        public Builder service(String service) { this.service = service; return this; }
        public Builder resourceArn(String resourceArn) { this.resourceArn = resourceArn; return this; }
        public Builder osFamily(OsFamily osFamily) { this.osFamily = osFamily; return this; }
        public Builder os(OperatingSystemMetadata os) { this.os = os; return this; }
        public Builder tag(ResourceTag tag) { if (tag != null) tags.add(tag); return this; }

        public ComputeTarget build() {
            return new ComputeTarget(id, name, service, resourceArn, osFamily, os, tags);
        }
    }
}
