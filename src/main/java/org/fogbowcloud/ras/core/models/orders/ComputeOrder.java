package org.fogbowcloud.ras.core.models.orders;

import org.fogbowcloud.ras.core.models.ResourceType;
import org.fogbowcloud.ras.core.models.quotas.allocation.ComputeAllocation;
import org.fogbowcloud.ras.core.models.tokens.FederationUserToken;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;


@Entity
@Table(name = "compute_order_table")
public class ComputeOrder extends Order {
    public static final String DEFAULT_NAME_PREFIX = "fogbow-compute-instance-";

    private static final long serialVersionUID = 1L;
    @Column
    private String instanceName;
    @Column
    private int vCPU;
    /**
     * Memory attribute, must be set in MB.
     */
    @Column
    private int memory;
    /**
     * Disk attribute, must be set in GB.
     */
    @Column
    private int disk;
    @Column
    private String imageId;
    @Embedded
    private UserData userData;
    @Column
    private String publicKey;
    @Embedded
    private ComputeAllocation actualAllocation;
    @Column
    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> networksId;

    public ComputeOrder() {
        super(UUID.randomUUID().toString());
    }

    /**
     * Creating Order with predefined Id.
     */
    public ComputeOrder(String id, FederationUserToken federationUserToken, String requestingMember,
                        String providingMember, String instanceName, int vCPU, int memory, int disk, String imageId,
                        UserData userData, String publicKey, List<String> networksId) {
        super(id, federationUserToken, requestingMember, providingMember);
        this.instanceName = instanceName;
        this.vCPU = vCPU;
        this.memory = memory;
        this.disk = disk;
        this.imageId = imageId;
        this.userData = userData;
        this.publicKey = publicKey;
        this.networksId = networksId;
        this.actualAllocation = new ComputeAllocation();
    }

    public ComputeOrder(FederationUserToken federationUserToken, String requestingMember, String providingMember,
                        String name, int vCPU, int memory, int disk, String imageId, UserData userData, String publicKey,
                        List<String> networksId) {
        this(UUID.randomUUID().toString(), federationUserToken, requestingMember, providingMember,
             name, vCPU, memory, disk, imageId, userData, publicKey, networksId);
    }

    public ComputeAllocation getActualAllocation() {
        return actualAllocation;
    }

    public void setActualAllocation(ComputeAllocation actualAllocation) {
        this.actualAllocation = actualAllocation;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public int getvCPU() {
        return vCPU;
    }

    public int getMemory() {
        return memory;
    }

    public int getDisk() {
        return disk;
    }

    public String getImageId() {
        return imageId;
    }

    public UserData getUserData() {
        return userData;
    }

    @Override
    public ResourceType getType() {
        return ResourceType.COMPUTE;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public List<String> getNetworksId() {
        if (networksId == null) {
            return Collections.unmodifiableList(new ArrayList<>());
        }
        return Collections.unmodifiableList(this.networksId);
    }

    public void setNetworksId(List<String> networksId) {
        this.networksId = networksId;
    }

	@Override
	public String getSpec() {
		return this.actualAllocation.getvCPU() + "/" + this.actualAllocation.getRam();
	}
}
