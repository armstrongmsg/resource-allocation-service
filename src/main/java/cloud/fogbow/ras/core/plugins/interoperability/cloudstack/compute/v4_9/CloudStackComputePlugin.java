package cloud.fogbow.ras.core.plugins.interoperability.cloudstack.compute.v4_9;

import cloud.fogbow.common.exceptions.*;
import cloud.fogbow.common.models.CloudStackUser;
import cloud.fogbow.common.util.PropertiesUtil;
import cloud.fogbow.common.util.connectivity.cloud.cloudstack.CloudStackHttpClient;
import cloud.fogbow.common.util.connectivity.cloud.cloudstack.CloudStackHttpToFogbowExceptionMapper;
import cloud.fogbow.common.util.connectivity.cloud.cloudstack.CloudStackUrlUtil;
import cloud.fogbow.ras.api.http.response.ComputeInstance;
import cloud.fogbow.ras.api.http.response.InstanceState;
import cloud.fogbow.ras.api.http.response.NetworkSummary;
import cloud.fogbow.ras.api.http.response.quotas.allocation.ComputeAllocation;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.constants.SystemConstants;
import cloud.fogbow.ras.core.models.ResourceType;
import cloud.fogbow.ras.core.models.orders.ComputeOrder;
import cloud.fogbow.ras.core.plugins.interoperability.ComputePlugin;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.CloudStackStateMapper;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.publicip.v4_9.CloudStackPublicIpPlugin;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.volume.v4_9.GetAllDiskOfferingsRequest;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.volume.v4_9.GetAllDiskOfferingsResponse;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.volume.v4_9.GetVolumeRequest;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.volume.v4_9.GetVolumeResponse;
import cloud.fogbow.ras.core.plugins.interoperability.util.DefaultLaunchCommandGenerator;
import cloud.fogbow.ras.core.plugins.interoperability.util.LaunchCommandGenerator;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.apache.log4j.Logger;
import javax.annotation.Nullable;
import java.util.*;

public class CloudStackComputePlugin implements ComputePlugin<CloudStackUser> {
    private static final Logger LOGGER = Logger.getLogger(CloudStackComputePlugin.class);

    protected static final String CLOUDUSER_NULL_EXCEPTION_MSG =
            String.format(Messages.Error.IRREGULAR_VALUE_NULL_EXCEPTION_MSG, "Cloud User");
    private static final String DEFAULT_VOLUME_TYPE = "ROOT";

    protected static final String ZONE_ID_KEY = "zone_id";
    protected static final String EXPUNGE_ON_DESTROY_KEY = "expunge_on_destroy";
    protected static final String FOGBOW_TAG_SEPARATOR = ":";
    private static final String CLOUDSTACK_URL = "cloudstack_api_url";
    protected static final double GIGABYTE_IN_BYTES = Math.pow(1024, 3);
    protected static final int UNKNOWN_DISK_VALUE = -1;

    private LaunchCommandGenerator launchCommandGenerator;
    private CloudStackHttpClient client;
    private String expungeOnDestroy;
    private String defaultNetworkId;
    private Properties properties;
    private String cloudStackUrl;
    private String zoneId;

    public CloudStackComputePlugin(String confFilePath) throws FatalErrorException {
        this.properties = PropertiesUtil.readProperties(confFilePath);

        this.cloudStackUrl = this.properties.getProperty(CLOUDSTACK_URL);
        this.zoneId = this.properties.getProperty(ZONE_ID_KEY);
        this.expungeOnDestroy = this.properties.getProperty(EXPUNGE_ON_DESTROY_KEY, "true");
        this.defaultNetworkId = this.properties.getProperty(CloudStackPublicIpPlugin.DEFAULT_NETWORK_ID_KEY);
        this.client = new CloudStackHttpClient();
        this.launchCommandGenerator = new DefaultLaunchCommandGenerator();
    }

    @VisibleForTesting
    CloudStackComputePlugin() {}

    @Override
    public boolean isReady(String cloudState) {
        return CloudStackStateMapper.map(ResourceType.COMPUTE, cloudState).equals(InstanceState.READY);
    }

    @Override
    public boolean hasFailed(String cloudState) {
        return CloudStackStateMapper.map(ResourceType.COMPUTE, cloudState).equals(InstanceState.FAILED);
    }

    @Override
    public String requestInstance(ComputeOrder computeOrder, final CloudStackUser cloudUser) throws FogbowException {
        String templateId = computeOrder.getImageId();
        if (templateId == null) {
            String errorMsg = Messages.Error.UNABLE_TO_COMPLETE_REQUEST_CLOUDSTACK;
            LOGGER.error(errorMsg);
            throw new InvalidParameterException(errorMsg);
        }

        String userData = this.launchCommandGenerator.createLaunchCommand(computeOrder);
        String networksId = normalizeNetworksID(computeOrder);

        GetAllServiceOfferingsResponse.ServiceOffering serviceOffering = getServiceOffering(computeOrder, cloudUser);
        if (serviceOffering == null) {
            String errorMsg = Messages.Error.UNABLE_TO_COMPLETE_REQUEST_SERVICE_OFFERING_CLOUDSTACK;
            LOGGER.error(errorMsg);
            throw new NoAvailableResourcesException(errorMsg);
        }

        int disk = computeOrder.getDisk();
        GetAllDiskOfferingsResponse.DiskOffering diskOffering = getDiskOffering(disk, cloudUser);
        if (diskOffering == null) {
            String errorMsg = Messages.Error.UNABLE_TO_COMPLETE_REQUEST_DISK_OFFERING_CLOUDSTACK;
            LOGGER.error(errorMsg);
            throw new NoAvailableResourcesException(errorMsg);
        }

        String instanceName = normalizeInstanceName(computeOrder.getName());

        DeployVirtualMachineRequest request = new DeployVirtualMachineRequest.Builder()
                .serviceOfferingId(serviceOffering.getId())
                .templateId(templateId)
                .zoneId(this.zoneId)
                .name(instanceName)
                .diskOfferingId(diskOffering.getId())
                .userData(userData)
                .networksId(networksId)
                .build(this.cloudStackUrl);

        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudUser.getToken());

        String jsonResponse = null;
        DeployVirtualMachineResponse response = null;
        try {
            jsonResponse = doGet(request.getUriBuilder().toString(), cloudUser);

            synchronized (computeOrder) {
                ComputeAllocation actualAllocation = new ComputeAllocation(
                        serviceOffering.getCpuNumber(),
                        serviceOffering.getMemory(),
                        1,
                        diskOffering.getDiskSize());
                computeOrder.setActualAllocation(actualAllocation);
            }

            response = DeployVirtualMachineResponse.fromJson(jsonResponse);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowExceptionMapper.map(e);
        }

        return response.getId();
    }

    @Override
    public ComputeInstance getInstance(ComputeOrder order, CloudStackUser cloudUser)
            throws FogbowException {

        GetVirtualMachineRequest request = new GetVirtualMachineRequest.Builder()
                .id(order.getInstanceId())
                .build(this.cloudStackUrl);

        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudUser.getToken());

        String jsonResponse = null;
        GetVirtualMachineResponse computeResponse = null;
        try {
            jsonResponse = doGet(request.getUriBuilder().toString(), cloudUser);
            computeResponse = GetVirtualMachineResponse.fromJson(jsonResponse);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowExceptionMapper.map(e);
        }

        List<GetVirtualMachineResponse.VirtualMachine> vms = computeResponse.getVirtualMachines();
        if (vms != null) {
            return getComputeInstance(vms.get(0), cloudUser);
        } else {
            throw new InstanceNotFoundException();
        }
    }

    @Override
    public void deleteInstance(ComputeOrder order, CloudStackUser cloudUser) throws FogbowException {
        DestroyVirtualMachineRequest request = new DestroyVirtualMachineRequest.Builder()
                .id(order.getInstanceId())
                .expunge(this.expungeOnDestroy)
                .build(this.cloudStackUrl);

        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudUser.getToken());

        try {
            doGet(request.getUriBuilder().toString(), cloudUser);
            LOGGER.info(String.format(Messages.Info.DELETING_INSTANCE, order.getInstanceId(), cloudUser.getToken()));
        } catch (HttpResponseException e) {
            LOGGER.error(String.format(Messages.Error.UNABLE_TO_DELETE_INSTANCE, order.getInstanceId()), e);
            CloudStackHttpToFogbowExceptionMapper.map(e);
        }
    }

    @Nullable
    @VisibleForTesting
    GetAllServiceOfferingsResponse.ServiceOffering getServiceOffering(
            ComputeOrder computeOrder, CloudStackUser cloudUser) throws FogbowException {

        GetAllServiceOfferingsResponse serviceOfferingsResponse = getServiceOfferings(cloudUser);
        List<GetAllServiceOfferingsResponse.ServiceOffering> serviceOfferings = serviceOfferingsResponse.
                getServiceOfferings();

        if (serviceOfferings == null || serviceOfferings.isEmpty()) {
            return null;
        }

        List<GetAllServiceOfferingsResponse.ServiceOffering> toRemove = new ArrayList<>();
        if (computeOrder.getRequirements() != null && computeOrder.getRequirements().size() > 0) {
            for (Map.Entry<String, String> tag : computeOrder.getRequirements().entrySet()) {
                String concatenatedTag = tag.getKey() + FOGBOW_TAG_SEPARATOR + tag.getValue();

                for (GetAllServiceOfferingsResponse.ServiceOffering serviceOffering : serviceOfferings) {
                    if (serviceOffering.getTags() == null) {
                        toRemove.add(serviceOffering);
                        continue;
                    }

                    List<String> tags = new ArrayList<>(Arrays.asList(serviceOffering.getTags().split(",")));
                    if (!tags.contains(concatenatedTag)) {
                        toRemove.add(serviceOffering);
                    }
                }
            }
        }

        serviceOfferings.removeAll(toRemove);

        for (GetAllServiceOfferingsResponse.ServiceOffering serviceOffering : serviceOfferings) {
            if (serviceOffering.getCpuNumber() >= computeOrder.getvCPU() &&
                    serviceOffering.getMemory() >= computeOrder.getMemory()) {
                return serviceOffering;
            }
        }

        return null;
    }

    @VisibleForTesting
    GetAllServiceOfferingsResponse getServiceOfferings(final CloudStackUser cloudUser) throws FogbowException {
        if (cloudUser == null) {
            throw new FogbowException(CLOUDUSER_NULL_EXCEPTION_MSG);
        }

        GetAllServiceOfferingsRequest request = new GetAllServiceOfferingsRequest.Builder().build(this.cloudStackUrl);
        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudUser.getToken());

        String jsonResponse = null;
        GetAllServiceOfferingsResponse getAllServiceOfferingsResponse = null;
        try {
            jsonResponse = doGet(request.getUriBuilder().toString(), cloudUser);
            getAllServiceOfferingsResponse = GetAllServiceOfferingsResponse.fromJson(jsonResponse);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowExceptionMapper.map(e);
        }

        return getAllServiceOfferingsResponse;
    }

    @Nullable
    @VisibleForTesting
    GetAllDiskOfferingsResponse.DiskOffering getDiskOffering(int diskSize, CloudStackUser cloudUser)
            throws FogbowException {

        GetAllDiskOfferingsResponse diskOfferingsResponse = getDiskOfferings(cloudUser);
        List<GetAllDiskOfferingsResponse.DiskOffering> diskOfferings = diskOfferingsResponse.getDiskOfferings();

        if (diskOfferings != null) {
            for (GetAllDiskOfferingsResponse.DiskOffering diskOffering : diskOfferings) {
                if (diskOffering.getDiskSize() >= diskSize) {
                    return diskOffering;
                }
            }
        }

        return null;
    }

    @VisibleForTesting
    GetAllDiskOfferingsResponse getDiskOfferings(final CloudStackUser cloudUser) throws FogbowException {
        if (cloudUser == null) {
            throw new FogbowException(CLOUDUSER_NULL_EXCEPTION_MSG);
        }

        GetAllDiskOfferingsRequest request = new GetAllDiskOfferingsRequest.Builder().build(this.cloudStackUrl);
        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudUser.getToken());

        String jsonResponse = null;
        GetAllDiskOfferingsResponse getAllDiskOfferingsResponse = null;
        try {
            jsonResponse = doGet(request.getUriBuilder().toString(), cloudUser);
            getAllDiskOfferingsResponse = GetAllDiskOfferingsResponse.fromJson(jsonResponse);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowExceptionMapper.map(e);
        }

        return getAllDiskOfferingsResponse;
    }

    @VisibleForTesting
    String normalizeNetworksID(final ComputeOrder computeOrder) {
        List<String> networks = new ArrayList<>();
        networks.add(this.defaultNetworkId);
        List<String> userDefinedNetworks = computeOrder.getNetworkIds();
        if (!userDefinedNetworks.isEmpty()) {
            networks.addAll(userDefinedNetworks);
        }
        return StringUtils.join(networks, ",");
    }

    @VisibleForTesting
    String normalizeInstanceName(final String instanceName) {
        return instanceName != null ? instanceName
                : SystemConstants.FOGBOW_INSTANCE_NAME_PREFIX + getRandomUUID();
    }

    @VisibleForTesting
    ComputeInstance getComputeInstance(GetVirtualMachineResponse.VirtualMachine vm, CloudStackUser cloudUser) {
        String instanceId = vm.getId();
        String hostName = vm.getName();
        int vcpusCount = vm.getCpuNumber();
        int memory = vm.getMemory();

        int disk = UNKNOWN_DISK_VALUE;
        try {
            disk = getVirtualMachineDiskSize(instanceId, cloudUser);
        } catch (FogbowException e) {
            LOGGER.warn(String.format(Messages.Warn.UNABLE_TO_RETRIEVE_ROOT_VOLUME, vm.getId()), e);
        }

        String cloudStackState = vm.getState();
        GetVirtualMachineResponse.Nic[] nics = vm.getNic();
        List<String> addresses = new ArrayList<>();

        for (GetVirtualMachineResponse.Nic nic : nics) {
            addresses.add(nic.getIpAddress());
        }

        ComputeInstance computeInstance = new ComputeInstance(
                instanceId, cloudStackState, hostName, vcpusCount, memory, disk, addresses);

        // The default network is always included in the order by the CloudStack plugin, thus it should be added
        // in the map of networks in the ComputeInstance by the plugin. The remaining networks passed by the user
        // are appended by the LocalCloudConnector.
        List<NetworkSummary> computeNetworks = new ArrayList<>();
        computeNetworks.add(new NetworkSummary(this.defaultNetworkId, SystemConstants.DEFAULT_NETWORK_NAME));
        computeInstance.setNetworks(computeNetworks);
        return computeInstance;
    }

    @VisibleForTesting
    int getVirtualMachineDiskSize(String virtualMachineId, CloudStackUser cloudUser) throws FogbowException {
        GetVolumeRequest request = new GetVolumeRequest.Builder()
                .virtualMachineId(virtualMachineId)
                .type(DEFAULT_VOLUME_TYPE)
                .build(this.cloudStackUrl);

        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudUser.getToken());

        String jsonResponse = null;
        GetVolumeResponse volumeResponse = null;
        try {
            jsonResponse = doGet(request.getUriBuilder().toString(), cloudUser);
            volumeResponse = GetVolumeResponse.fromJson(jsonResponse);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowExceptionMapper.map(e);
        }

        List<GetVolumeResponse.Volume> volumes = volumeResponse.getVolumes();
        if (volumes != null) {
            GetVolumeResponse.Volume volume = volumes.get(0);
            long sizeInBytes = volume.getSize();
            return convertBytesToGigabyte(sizeInBytes);
        } else {
            throw new InstanceNotFoundException();
        }
    }

    @VisibleForTesting
    String doGet(String url, CloudStackUser cloudUser) throws HttpResponseException {
        try {
            return this.client.doGetRequest(url, cloudUser);
        } catch (FogbowException e) {
            throw  new HttpResponseException(HttpStatus.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @VisibleForTesting
    int convertBytesToGigabyte(long bytes) {
        return (int) (bytes / GIGABYTE_IN_BYTES);
    }

    protected String getRandomUUID() {
        return UUID.randomUUID().toString();
    }

    protected void setLaunchCommandGenerator(LaunchCommandGenerator commandGenerator) {
        this.launchCommandGenerator = commandGenerator;
    }

    @VisibleForTesting
    void setClient(CloudStackHttpClient client) {
        this.client = client;
    }

    @VisibleForTesting
    String getCloudStackUrl() {
        return cloudStackUrl;
    }
}
