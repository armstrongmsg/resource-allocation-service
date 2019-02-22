package cloud.fogbow.ras.core.models.securityrules;

public enum Direction {
    IN("ingress"), OUT("egress");

    private String direction;

    Direction(String direction) {
        this.direction = direction;
    }

    @Override
    public String toString() {
        return this.direction;
    }
}