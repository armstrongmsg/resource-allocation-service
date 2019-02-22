package cloud.fogbow.ras.core.plugins.interoperability.cloudstack.compute.v4_9;

import cloud.fogbow.common.exceptions.*;
import cloud.fogbow.common.models.CloudToken;
import cloud.fogbow.common.util.PropertiesUtil;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.core.models.ResourceType;
import cloud.fogbow.ras.core.models.instances.ComputeInstance;
import cloud.fogbow.ras.core.models.instances.InstanceState;
import cloud.fogbow.ras.core.models.orders.ComputeOrder;
import cloud.fogbow.ras.core.plugins.interoperability.ComputePlugin;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.CloudStackHttpClient;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.CloudStackHttpToFogbowExceptionMapper;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.CloudStackStateMapper;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.CloudStackUrlUtil;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.publicip.v4_9.CloudStackPublicIpPlugin;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.volume.v4_9.GetAllDiskOfferingsRequest;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.volume.v4_9.GetAllDiskOfferingsResponse;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.volume.v4_9.GetVolumeRequest;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.volume.v4_9.GetVolumeResponse;
import cloud.fogbow.ras.core.plugins.interoperability.util.DefaultLaunchCommandGenerator;
import cloud.fogbow.ras.core.plugins.interoperability.util.LaunchCommandGenerator;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.HttpResponseException;
import org.apache.log4j.Logger;

import java.util.*;

public class CloudStackComputePlugin implements ComputePlugin {
    private static final Logger LOGGER = Logger.getLogger(CloudStackComputePlugin.class);

    public static final String ZONE_ID_KEY = "zone_id";
    public static final String EXPUNGE_ON_DESTROY_KEY = "expunge_on_destroy";
    public static final String DEFAULT_VOLUME_TYPE = "ROOT";
    public static final String FOGBOW_INSTANCE_NAME = "fogbow-compute-instance-";
    public static final String DEFAULT_NETWORK_NAME = "default";
    public static final String FOGBOW_TAG_SEPARATOR = ":";
    public static final String CLOUDSTACK_URL = "cloudstack_api_url";

    private String cloudStackUrl;
    private String zoneId;
    private String expungeOnDestroy;
    private String defaultNetworkId;

    private CloudStackHttpClient client;
    private LaunchCommandGenerator launchCommandGenerator;
    private Properties properties;

    public CloudStackComputePlugin(String confFilePath) throws FatalErrorException {
        this.properties = PropertiesUtil.readProperties(confFilePath);

        this.cloudStackUrl = this.properties.getProperty(CLOUDSTACK_URL);
        this.zoneId = this.properties.getProperty(ZONE_ID_KEY);
        this.expungeOnDestroy = this.properties.getProperty(EXPUNGE_ON_DESTROY_KEY, "true");
        this.defaultNetworkId = this.properties.getProperty(CloudStackPublicIpPlugin.DEFAULT_NETWORK_ID_KEY);
        this.client = new CloudStackHttpClient();
        this.launchCommandGenerator = new DefaultLaunchCommandGenerator();
    }

    // NOTE(pauloewerton): used for testing only.
    public CloudStackComputePlugin() {}

    @Override
    public String requestInstance(ComputeOrder computeOrder, CloudToken cloudStackToken) throws FogbowException {
        String templateId = computeOrder.getImageId();
        if (templateId == null || this.zoneId == null || this.defaultNetworkId == null) {
            LOGGER.error(Messages.Error.UNABLE_TO_COMPLETE_REQUEST);
            throw new InvalidParameterException();
        }

        String userData = this.launchCommandGenerator.createLaunchCommand(computeOrder);

        String networksId = resolveNetworksId(computeOrder);

        String serviceOfferingId = getServiceOfferingId(computeOrder, cloudStackToken);
        if (serviceOfferingId == null) {
            LOGGER.error(Messages.Error.UNABLE_TO_COMPLETE_REQUEST);
            throw new NoAvailableResourcesException();
        }

        int disk = computeOrder.getDisk();
        // NOTE(pauloewerton): cloudstack allows creating a vm without explicitly choosing a disk size. in that case,
        // a minimum root disk for the selected template is created. also zeroing disk param in case no minimum disk
        // offering is found.
        String diskOfferingId = disk > 0 ? getDiskOfferingId(disk, cloudStackToken) : null;

        String instanceName = computeOrder.getName();
        if (instanceName == null) instanceName = FOGBOW_INSTANCE_NAME + getRandomUUID();

        // NOTE(pauloewerton): hypervisor param is required in case of ISO image. i haven't found any clue pointing that
        // ISO images were being used in mono though.
        DeployVirtualMachineRequest request = new DeployVirtualMachineRequest.Builder()
                .serviceOfferingId(serviceOfferingId)
                .templateId(templateId)
                .zoneId(this.zoneId)
                .name(instanceName)
                .diskOfferingId(diskOfferingId)
                .userData(userData)
                .networksId(networksId)
                .build(this.cloudStackUrl);

        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudStackToken.getTokenValue());

        String jsonResponse = null;
        try {
            jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), cloudStackToken);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowExceptionMapper.map(e);
        }

        DeployVirtualMachineResponse response = DeployVirtualMachineResponse.fromJson(jsonResponse);

        return response.getId();
    }

    @Override
    public ComputeInstance getInstance(String computeInstanceId, CloudToken cloudStackToken) throws FogbowException {
        GetVirtualMachineRequest request = new GetVirtualMachineRequest.Builder()
                .id(computeInstanceId)
                .build(this.cloudStackUrl);

        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudStackToken.getTokenValue());

        String jsonResponse = null;
        try {
            jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), cloudStackToken);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowExceptionMapper.map(e);
        }

        GetVirtualMachineResponse computeResponse = GetVirtualMachineResponse.fromJson(jsonResponse);
        List<GetVirtualMachineResponse.VirtualMachine> vms = computeResponse.getVirtualMachines();
        if (vms != null) {
            return getComputeInstance(vms.get(0), cloudStackToken);
        } else {
            throw new InstanceNotFoundException();
        }
    }

    @Override
    public void deleteInstance(String computeInstanceId, CloudToken cloudStackToken) throws FogbowException {
        DestroyVirtualMachineRequest request = new DestroyVirtualMachineRequest.Builder()
                .id(computeInstanceId)
                .expunge(this.expungeOnDestroy)
                .build(this.cloudStackUrl);

        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudStackToken.getTokenValue());

        try {
            this.client.doGetRequest(request.getUriBuilder().toString(), cloudStackToken);
        } catch (HttpResponseException e) {
            LOGGER.error(String.format(Messages.Error.UNABLE_TO_DELETE_INSTANCE, computeInstanceId), e);
            CloudStackHttpToFogbowExceptionMapper.map(e);
        }

        LOGGER.info(String.format(Messages.Info.DELETING_INSTANCE, computeInstanceId, cloudStackToken.getTokenValue()));
    }

    private String resolveNetworksId(ComputeOrder computeOrder) {
        List<String> requestedNetworksId = new ArrayList<>();

        requestedNetworksId.add(this.defaultNetworkId);
        requestedNetworksId.addAll(computeOrder.getNetworkIds());
        computeOrder.setNetworkIds(requestedNetworksId);

        return StringUtils.join(requestedNetworksId, ",");
    }

    private String getServiceOfferingId(ComputeOrder computeOrder, CloudToken cloudStackToken) throws FogbowException {
        GetAllServiceOfferingsResponse serviceOfferingsResponse = getServiceOfferings(cloudStackToken);
        List<GetAllServiceOfferingsResponse.ServiceOffering> serviceOfferings = serviceOfferingsResponse.
                getServiceOfferings();

        if (serviceOfferings == null) throw new NoAvailableResourcesException();

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
                return serviceOffering.getId();
            }
        }

        return null;
    }

    private GetAllServiceOfferingsResponse getServiceOfferings(CloudToken cloudStackToken) throws FogbowException {
        GetAllServiceOfferingsRequest request = new GetAllServiceOfferingsRequest.Builder().build(this.cloudStackUrl);
        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudStackToken.getTokenValue());

        String jsonResponse = null;
        try {
            jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), cloudStackToken);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowExceptionMapper.map(e);
        }

        GetAllServiceOfferingsResponse serviceOfferingsResponse = GetAllServiceOfferingsResponse.fromJson(jsonResponse);

        return serviceOfferingsResponse;
    }

    private String getDiskOfferingId(int diskSize, CloudToken cloudStackToken) throws FogbowException {
        GetAllDiskOfferingsResponse diskOfferingsResponse = getDiskOfferings(cloudStackToken);
        List<GetAllDiskOfferingsResponse.DiskOffering> diskOfferings = diskOfferingsResponse.getDiskOfferings();

        if (diskOfferings != null) {
            for (GetAllDiskOfferingsResponse.DiskOffering diskOffering : diskOfferings) {
                if (diskOffering.getDiskSize() >= diskSize) {
                    return diskOffering.getId();
                }
            }
        }

        return null;
    }

    private GetAllDiskOfferingsResponse getDiskOfferings(CloudToken cloudStackToken) throws FogbowException {
        GetAllDiskOfferingsRequest request = new GetAllDiskOfferingsRequest.Builder().build(this.cloudStackUrl);
        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudStackToken.getTokenValue());

        String jsonResponse = null;
        try {
            jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), cloudStackToken);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowExceptionMapper.map(e);
        }

        GetAllDiskOfferingsResponse diskOfferingsResponse = GetAllDiskOfferingsResponse.fromJson(jsonResponse);

        return diskOfferingsResponse;
    }

    private ComputeInstance getComputeInstance(GetVirtualMachineResponse.VirtualMachine vm, CloudToken cloudStackToken) {
        String instanceId = vm.getId();
        String hostName = vm.getName();
        int vcpusCount = vm.getCpuNumber();
        int memory = vm.getMemory();

        int disk = -1;
        try {
            disk = getVirtualMachineDiskSize(instanceId, cloudStackToken);
        } catch (FogbowException e) {
            LOGGER.warn(String.format(Messages.Warn.UNABLE_TO_RETRIEVE_ROOT_VOLUME, vm.getId()), e);
        }

        String cloudStackState = vm.getState();
        InstanceState fogbowState = CloudStackStateMapper.map(ResourceType.COMPUTE, cloudStackState);

        GetVirtualMachineResponse.Nic[] nics = vm.getNic();
        List<String> addresses = new ArrayList<>();

        for (GetVirtualMachineResponse.Nic nic : nics) {
            addresses.add(nic.getIpAddress());
        }

        ComputeInstance computeInstance = new ComputeInstance(
                instanceId, fogbowState, hostName, vcpusCount, memory, disk, addresses);

        Map<String, String> computeNetworks = new HashMap<>();
        computeNetworks.put(this.defaultNetworkId, DEFAULT_NETWORK_NAME);
        computeInstance.setNetworks(computeNetworks);

        return computeInstance;
    }

    private int getVirtualMachineDiskSize(String virtualMachineId, CloudToken cloudStackToken) throws FogbowException {
        GetVolumeRequest request = new GetVolumeRequest.Builder()
                .virtualMachineId(virtualMachineId)
                .type(DEFAULT_VOLUME_TYPE)
                .build(this.cloudStackUrl);

        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudStackToken.getTokenValue());

        String jsonResponse = null;
        try {
            jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), cloudStackToken);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowExceptionMapper.map(e);
        }

        GetVolumeResponse volumeResponse = GetVolumeResponse.fromJson(jsonResponse);
        List<GetVolumeResponse.Volume> volumes = volumeResponse.getVolumes();
        if (volumes != null) {
            long sizeInBytes = volumes.get(0).getSize();
            int sizeInGigabytes = (int) (sizeInBytes / Math.pow(1024, 3));
            return sizeInGigabytes;
        } else {
            throw new InstanceNotFoundException();
        }
    }

    protected String getRandomUUID() {
        return UUID.randomUUID().toString();
    }

    // Methods below are used for testing only
    protected void setClient(CloudStackHttpClient client) {
        this.client = client;
    }

    protected void setLaunchCommandGenerator(LaunchCommandGenerator commandGenerator) {
        this.launchCommandGenerator = commandGenerator;
    }
}