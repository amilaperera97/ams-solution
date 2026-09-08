package uk.co.ams.certplatform.domain.model;

public class OperatingSystemMetadata {
    private String family;
    private String name;
    private String version;
    private String architecture;

    public OperatingSystemMetadata() {}

    public String getFamily() { return family; }
    public void setFamily(String family) { this.family = family; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getArchitecture() { return architecture; }
    public void setArchitecture(String architecture) { this.architecture = architecture; }
}
