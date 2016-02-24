package net.floodlightcontroller.servicechaining;

public class ServiceNode {
    public enum InsertionType {
        L3, L2, BUMPINTHEWIRE, TAP, UNKNOWN
    }

    public enum Direction {
        INGRESS, EGRESS, ANY
    }

    protected String name;
    protected String tenant;
    protected InsertionType type;

    public ServiceNode(String tenant, String name, InsertionType type) {
        this.tenant = tenant;
        this.name = name;
        this.type = type;
    }

    public String getTenant() {
        return tenant;
    }

    public String getName() {
        return name;
    }

    public InsertionType getServiceType() {
        return this.type;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((tenant == null) ? 0 : tenant.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ServiceNode other = (ServiceNode) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        if (tenant == null) {
            if (other.tenant != null)
                return false;
        } else if (!tenant.equals(other.tenant))
            return false;
        if (type != other.type)
            return false;
        return true;
    }
}
