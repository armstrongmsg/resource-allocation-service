package cloud.fogbow.ras.core.models.orders;

import cloud.fogbow.common.models.FederationUser;
import cloud.fogbow.ras.core.models.ResourceType;
import cloud.fogbow.ras.core.models.UserData;
import cloud.fogbow.ras.core.models.quotas.allocation.ComputeAllocation;
import org.apache.log4j.Logger;

import javax.persistence.*;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "compute_order_table")
public class ComputeOrder extends Order {
    private static final long serialVersionUID = 1L;

    private static final String NAME_COLUMN_NAME = "name";
    private static final String IMAGE_ID_COLUMN_NAME = "image_id";
    private static final String PUBLIC_KEY_COLUMN_NAME = "public_key";

    public static final int PUBLIC_KEY_MAX_SIZE = 1024;

    @Transient
    private transient final Logger LOGGER = Logger.getLogger(ComputeOrder.class);

    @Column
    private int vCPU;

    // Memory attribute, must be set in MB.
    @Column
    private int memory;

    // Disk attribute, must be set in GB.
    @Column
    private int disk;

    @Embedded
    private ArrayList<UserData> userData;

    @Size(max = Order.FIELDS_MAX_SIZE)
    @Column(name = NAME_COLUMN_NAME)
    private String name;

    @Size(max = Order.FIELDS_MAX_SIZE)
    @Column(name = IMAGE_ID_COLUMN_NAME)
    private String imageId;

    @Size(max = PUBLIC_KEY_MAX_SIZE)
    @Column(name = PUBLIC_KEY_COLUMN_NAME)
    private String publicKey;

    @Embedded
    private ComputeAllocation actualAllocation;

    @Column
    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> networkIds;

    public ComputeOrder() {
        this(UUID.randomUUID().toString());
    }

    public ComputeOrder(String id) {
        super(id);
    }

    public ComputeOrder(String id, FederationUser federationUser, String requestingMember, String providingMember,
                        String cloudName, String name, int vCPU, int memory, int disk, String imageId, ArrayList<UserData> userData, String publicKey,
                        List<String> networkIds) {
        super(id, providingMember, cloudName, federationUser, requestingMember);
        this.name = name;
        this.vCPU = vCPU;
        this.memory = memory;
        this.disk = disk;
        this.imageId = imageId;
        this.userData = userData;
        this.publicKey = publicKey;
        this.networkIds = networkIds;
        this.actualAllocation = new ComputeAllocation();
    }

    public ComputeOrder(String providingMember, String cloudName, String name, int vCPU, int memory, int disk, String imageId,
                        ArrayList<UserData> userData, String publicKey, List<String> networkIds) {
        this(null, null, providingMember, cloudName, name, vCPU, memory, disk, imageId,
                userData, publicKey, networkIds);
    }

    public ComputeOrder(FederationUser federationUser, String requestingMember, String providingMember,
                        String cloudName, String name, int vCPU, int memory, int disk, String imageId, ArrayList<UserData> userData, String publicKey,
                        List<String> networkIds) {
        this(UUID.randomUUID().toString(), federationUser, requestingMember, providingMember, cloudName, name, vCPU, memory,
                disk, imageId, userData, publicKey, networkIds);
    }

    public ComputeAllocation getActualAllocation() {
        return actualAllocation;
    }

    public void setActualAllocation(ComputeAllocation actualAllocation) {
        this.actualAllocation = actualAllocation;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public ArrayList<UserData> getUserData() {
        return userData;
    }

    public void setUserData(ArrayList<UserData> userData) {
        this.userData = userData;
    }

    @Override
    public ResourceType getType() {
        return ResourceType.COMPUTE;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public List<String> getNetworkIds() {
        if (networkIds == null) {
            return Collections.unmodifiableList(new ArrayList<>());
        }
        return Collections.unmodifiableList(this.networkIds);
    }

    public void setNetworkIds(List<String> networkIds) {
        this.networkIds = networkIds;
    }

    @Override
    public String getSpec() {
        if (this.actualAllocation == null) {
            return "";
        }
        return this.actualAllocation.getvCPU() + "/" + this.actualAllocation.getRam();
    }

    @Override
    public Logger getLogger() {
        return LOGGER;
    }

    @PrePersist
    protected void checkColumnsSizes() {
        this.name = treatValue(this.name, NAME_COLUMN_NAME, Order.FIELDS_MAX_SIZE);
        this.imageId = treatValue(this.imageId, IMAGE_ID_COLUMN_NAME, Order.FIELDS_MAX_SIZE);
        this.publicKey = treatValue(this.publicKey, PUBLIC_KEY_COLUMN_NAME, PUBLIC_KEY_MAX_SIZE);
    }
}