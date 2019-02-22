package cloud.fogbow.ras.core.models.instances;

public class Instance {
    private String id;
    private InstanceState state;
    private String provider;
    private String cloudName;

    public Instance(String id) {
        this.id = id;
    }

    public Instance(String id, InstanceState state) {
        this(id);
        this.state = state;
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public InstanceState getState() {
        return this.state;
    }

    public void setState(InstanceState state) {
        this.state = state;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public void setCloudName(String cloudName) {
        this.cloudName = cloudName;
    }

    public String getCloudName() {
        return cloudName;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((this.id == null) ? 0 : this.id.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        Instance other = (Instance) obj;
        if (this.id == null) {
            if (other.getId() != null) return false;
        } else if (!this.id.equals(other.getId())) return false;
        return true;
    }

    @Override
    public String toString() {
        return "Instance{" +
                "id='" + id + '\'' +
                ", state=" + state +
                ", provider='" + provider + '\'' +
                ", cloudName='" + cloudName + '\'' +
                '}';
    }
}