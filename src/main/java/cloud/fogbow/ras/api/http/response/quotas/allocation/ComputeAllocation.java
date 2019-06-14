package cloud.fogbow.ras.api.http.response.quotas.allocation;

import cloud.fogbow.ras.constants.ApiDocumentation;
import io.swagger.annotations.ApiModelProperty;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public class ComputeAllocation extends Allocation {
    @ApiModelProperty(position = 0, example = "2")
    @Column(name = "allocation_instances")
    private int instances;
    @ApiModelProperty(position = 1, example = "4")
    @Column(name = "allocation_vcpu")
    private int vCPU;
    @ApiModelProperty(position = 2, example = "8192")
    @Column(name = "allocation_ram")
    private int ram;
    @ApiModelProperty(position = 3, example = "30")
    @Column(name = "allocation_disk")
    private int disk;

    public ComputeAllocation(int vCPU, int ram, int instances, int disk) {
        this.vCPU = vCPU;
        this.ram = ram;
        this.instances = instances;
        this.disk = disk;
    }

    public ComputeAllocation(int vCPU, int ram, int instances) {
        this.vCPU = vCPU;
        this.ram = ram;
        this.instances = instances;
    }

    public ComputeAllocation() {
    }

    public int getvCPU() {
        return this.vCPU;
    }

    public int getRam() {
        return this.ram;
    }

    public int getInstances() {
        return this.instances;
    }

    public int getDisk() {
        return disk;
    }
}
