package org.fogbowcloud.ras.core.cloudconnector;

import org.apache.log4j.Logger;
import org.fogbowcloud.ras.core.InteroperabilityPluginInstantiator;
import org.fogbowcloud.ras.core.SharedOrderHolders;
import org.fogbowcloud.ras.core.constants.Messages;
import org.fogbowcloud.ras.core.exceptions.FogbowRasException;
import org.fogbowcloud.ras.core.exceptions.InstanceNotFoundException;
import org.fogbowcloud.ras.core.exceptions.InvalidParameterException;
import org.fogbowcloud.ras.core.exceptions.UnexpectedException;
import org.fogbowcloud.ras.core.models.ResourceType;
import org.fogbowcloud.ras.core.models.UserData;
import org.fogbowcloud.ras.core.models.images.Image;
import org.fogbowcloud.ras.core.models.instances.*;
import org.fogbowcloud.ras.core.models.orders.*;
import org.fogbowcloud.ras.core.models.quotas.Quota;
import org.fogbowcloud.ras.core.models.securityrules.SecurityRule;
import org.fogbowcloud.ras.core.models.tokens.FederationUserToken;
import org.fogbowcloud.ras.core.models.tokens.Token;
import org.fogbowcloud.ras.core.plugins.interoperability.genericrequest.GenericRequest;
import org.fogbowcloud.ras.core.plugins.interoperability.genericrequest.GenericRequestResponse;
import org.fogbowcloud.ras.core.plugins.mapper.FederationToLocalMapperPlugin;
import org.fogbowcloud.ras.core.plugins.interoperability.*;
import org.fogbowcloud.ras.core.plugins.interoperability.genericrequest.GenericRequestPlugin;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class LocalCloudConnector implements CloudConnector {
    private static final Logger LOGGER = Logger.getLogger(LocalCloudConnector.class);

    private FederationToLocalMapperPlugin mapperPlugin;
    private PublicIpPlugin publicIpPlugin;
    private AttachmentPlugin attachmentPlugin;
    private ComputePlugin computePlugin;
    private ComputeQuotaPlugin computeQuotaPlugin;
    private NetworkPlugin networkPlugin;
    private VolumePlugin volumePlugin;
    private ImagePlugin imagePlugin;
    private SecurityRulePlugin securityRulePlugin;
    private GenericRequestPlugin genericRequestPlugin;

    public LocalCloudConnector(String cloudName) {
        InteroperabilityPluginInstantiator instantiator = new InteroperabilityPluginInstantiator(cloudName);
        this.mapperPlugin = instantiator.getLocalUserCredentialsMapperPlugin();
        this.attachmentPlugin = instantiator.getAttachmentPlugin();
        this.computePlugin = instantiator.getComputePlugin();
        this.computeQuotaPlugin = instantiator.getComputeQuotaPlugin();
        this.networkPlugin = instantiator.getNetworkPlugin();
        this.volumePlugin = instantiator.getVolumePlugin();
        this.imagePlugin = instantiator.getImagePlugin();
        this.publicIpPlugin = instantiator.getPublicIpPlugin();
        this.securityRulePlugin = instantiator.getSecurityRulePlugin();
        this.genericRequestPlugin = instantiator.getGenericRequestPlugin();
    }

    @Override
    public String requestInstance(Order order) throws FogbowRasException, UnexpectedException {
        String requestInstance = null;
        Token token = this.mapperPlugin.map(order.getFederationUserToken());
        switch (order.getType()) {
            case COMPUTE:
                // As the order parameter came from the rest API, the NetworkInstanceIds in the order are actually
                // NetworkOrderIds, since these are the Ids that are known to users/applications using the API.
                // Thus, before requesting the plugin to create the Compute, we need to replace NetworkOrderIds by
                // NetworkInstanceIds, which are contained in the respective NetworkOrder.
                // We save the list of NetworkOrderIds in the original order, to restore these values, after
                // the Compute instance is requested in the cloud.
                ComputeOrder computeOrder = (ComputeOrder) order;
                List<String> savedNetworkOrderIds = computeOrder.getNetworkIds();
                List<String> networkInstanceIds = getNetworkInstanceIdsFromNetworkOrderIds(computeOrder);
                computeOrder.setNetworkIds(networkInstanceIds);
                try {
                    requestInstance = this.computePlugin.requestInstance(computeOrder, token);
                } catch (Throwable e) {
                    throw e;
                } finally {
                    computeOrder.setNetworkIds(savedNetworkOrderIds);
                }
                break;
            case NETWORK:
                NetworkOrder networkOrder = (NetworkOrder) order;
                requestInstance = this.networkPlugin.requestInstance(networkOrder, token);
                break;
            case VOLUME:
                VolumeOrder volumeOrder = (VolumeOrder) order;
                requestInstance = this.volumePlugin.requestInstance(volumeOrder, token);
                break;
            case ATTACHMENT:
                // Check if both the compute and the volume orders belong to the user issuing the attachment order
                AttachmentOrder attachmentOrder = (AttachmentOrder) order;
                String savedComputeOrderId = attachmentOrder.getComputeId();
                String savedVolumeOrderId = attachmentOrder.getVolumeId();
                Order attachmentComputeOrder = SharedOrderHolders.getInstance().getActiveOrdersMap().get(savedComputeOrderId);
                Order attachmentVolumeOrder = SharedOrderHolders.getInstance().getActiveOrdersMap().get(savedVolumeOrderId);
                String attachmentOrderUserId = attachmentOrder.getFederationUserToken().getUserId();
                String computeOrderUserId = attachmentComputeOrder.getFederationUserToken().getUserId();
                String volumeOrderUserId = attachmentVolumeOrder.getFederationUserToken().getUserId();
                if (!attachmentOrderUserId.equals(computeOrderUserId) ||
                        !attachmentOrderUserId.equals(volumeOrderUserId)) {
                    throw new InvalidParameterException(Messages.Exception.TRYING_TO_USE_RESOURCES_FROM_ANOTHER_USER);
                }
                // Check if both compute and volume belong to the requested provider
                String attachmentProvider = attachmentOrder.getProvider();
                String computeProvider = attachmentComputeOrder.getProvider();
                String volumeProvider = attachmentVolumeOrder.getProvider();
                if (!attachmentProvider.equals(computeProvider) ||
                        !attachmentProvider.equals(volumeProvider)) {
                    throw new InvalidParameterException(Messages.Exception.PROVIDERS_DONT_MATCH);
                }
                // As the order parameter came from the rest API, the Compute and Volume fields are actually
                // ComputeOrder and VolumeOrder Ids, since these are the Ids that are known to users/applications
                // using the API. Thus, before requesting the plugin to create the Attachment, we need to replace the
                // ComputeOrderId and the VolumeOrderId by their corresponding ComputeInstanceId and VolumeInstanceId.
                // We save the Order Ids in the original order, to restore these values, after the Attachment is
                // requested in the cloud.
                attachmentOrder.setComputeId(attachmentComputeOrder.getInstanceId());
                attachmentOrder.setVolumeId(attachmentVolumeOrder.getInstanceId());
                try {
                    requestInstance = this.attachmentPlugin.requestInstance(attachmentOrder, token);
                } catch (Throwable e) {
                    throw e;
                } finally {
                    attachmentOrder.setComputeId(savedComputeOrderId);
                    attachmentOrder.setVolumeId(savedVolumeOrderId);
                }
                break;
            case PUBLIC_IP:
                PublicIpOrder publicIpOrder = (PublicIpOrder) order;

                String computeOrderId = publicIpOrder.getComputeOrderId();

                Order retrievedComputeOrder = SharedOrderHolders.getInstance().getActiveOrdersMap()
                        .get(computeOrderId);

                String publicIpOrderUserId = publicIpOrder.getFederationUserToken().getUserId();
                String targetComputeOrderUserId = retrievedComputeOrder.getFederationUserToken().getUserId();
                if (!publicIpOrderUserId.equals(targetComputeOrderUserId)) {
                    throw new InvalidParameterException(Messages.Exception.TRYING_TO_USE_RESOURCES_FROM_ANOTHER_USER);
                }

                String computeInstanceId = retrievedComputeOrder.getInstanceId();
                if (computeInstanceId != null) {
                    requestInstance = this.publicIpPlugin.requestInstance(publicIpOrder, computeInstanceId, token);
                }
                break;
            default:
                throw new UnexpectedException(String.format(Messages.Exception.PLUGIN_FOR_REQUEST_INSTANCE_NOT_IMPLEMENTED, order.getType()));
        }
        if (requestInstance == null) {
            throw new UnexpectedException(Messages.Exception.NULL_VALUE_RETURNED);
        }
        return requestInstance;
    }

    @Override
    public void deleteInstance(Order order) throws FogbowRasException, UnexpectedException {
        try {
            if (order.getInstanceId() != null) {
                Token token = this.mapperPlugin.map(order.getFederationUserToken());
                switch (order.getType()) {
                    case COMPUTE:
                        this.computePlugin.deleteInstance(order.getInstanceId(), token);
                        break;
                    case VOLUME:
                        this.volumePlugin.deleteInstance(order.getInstanceId(), token);
                        break;
                    case NETWORK:
                        this.networkPlugin.deleteInstance(order.getInstanceId(), token);
                        break;
                    case ATTACHMENT:
                        this.attachmentPlugin.deleteInstance(order.getInstanceId(), token);
                        break;
                    case PUBLIC_IP:
                        String computeInstanceId = getComputeInstanceId((PublicIpOrder) order);
                        this.publicIpPlugin.deleteInstance(order.getInstanceId(), computeInstanceId, token);
                        break;
                    default:
                        LOGGER.error(String.format(Messages.Error.DELETE_INSTANCE_PLUGIN_NOT_IMPLEMENTED, order.getType()));
                        break;
                }
            } else {
                // If instanceId is null, then there is nothing to do.
                return;
            }
        } catch (InstanceNotFoundException e) {
            // This may happen if the resource-allocation-service crashed after the instance is deleted
            // but before the new state is updated in stable storage.
            LOGGER.warn(Messages.Warn.INSTANCE_ALREADY_DELETED);
            return;
        }
    }

    private String getComputeInstanceId(PublicIpOrder order) {
        PublicIpOrder publicIpOrder = order;
        String computeOrderId = publicIpOrder.getComputeOrderId();
        Order computeOrder = SharedOrderHolders.getInstance().getActiveOrdersMap().get(computeOrderId);
        return computeOrder == null ? null : computeOrder.getInstanceId();
    }

    @Override
    public Instance getInstance(Order order) throws FogbowRasException, UnexpectedException {
        Instance instance;
        Token token = this.mapperPlugin.map(order.getFederationUserToken());
        synchronized (order) {
            if (order.getOrderState() == OrderState.DEACTIVATED || order.getOrderState() == OrderState.CLOSED) {
                throw new InstanceNotFoundException();
            }
            String instanceId = order.getInstanceId();
            if (instanceId != null) {
                instance = getResourceInstance(order, order.getType(), token);
                // Setting instance common fields that do not need to be set by the plugin
                instance.setProvider(order.getProvider());
                // The user believes that the order id is actually the instance id.
                // So we need to set the instance id accordingly before returning the instance.
                instance.setId(order.getId());
            } else {
                // When there is no instance, an empty one is created with the appropriate state
                switch (order.getType()) {
                    case COMPUTE:
                        instance = new ComputeInstance(order.getId());
                        break;
                    case VOLUME:
                        instance = new VolumeInstance(order.getId());
                        break;
                    case NETWORK:
                        instance = new NetworkInstance(order.getId());
                        break;
                    case ATTACHMENT:
                        instance = new AttachmentInstance(order.getId());
                        break;
                    case PUBLIC_IP:
                        instance = new PublicIpInstance(order.getId());
                        break;
                    default:
                        throw new UnexpectedException(Messages.Exception.UNSUPPORTED_REQUEST_TYPE);
                }
                InstanceState instanceState = getInstanceStateBasedOnOrderState(order);
                instance.setState(instanceState);
            }
        }
        return instance;
    }

    @Override
    public Quota getUserQuota(FederationUserToken federationUserToken, ResourceType resourceType) throws
            FogbowRasException, UnexpectedException {
        Token token = this.mapperPlugin.map(federationUserToken);
        switch (resourceType) {
            case COMPUTE:
                return this.computeQuotaPlugin.getUserQuota(token);
            default:
                throw new UnexpectedException(String.format(Messages.Exception.QUOTA_ENDPOINT_NOT_IMPLEMENTED, resourceType));
        }
    }

    @Override
    public Map<String, String> getAllImages(FederationUserToken federationUserToken)
            throws FogbowRasException, UnexpectedException {
        Token token = this.mapperPlugin.map(federationUserToken);
        return this.imagePlugin.getAllImages(token);
    }

    @Override
    public Image getImage(String imageId, FederationUserToken federationUserToken)
            throws FogbowRasException, UnexpectedException {
        Token token = this.mapperPlugin.map(federationUserToken);
        return this.imagePlugin.getImage(imageId, token);
    }

    @Override
    public GenericRequestResponse genericRequest(GenericRequest genericRequest, FederationUserToken federationTokenUser)
            throws UnexpectedException, FogbowRasException {
        Token token = this.mapperPlugin.map(federationTokenUser);
        return this.genericRequestPlugin.redirectGenericRequest(genericRequest, token);
    }

    @Override
    public List<SecurityRule> getAllSecurityRules(Order order, FederationUserToken federationUserToken)
            throws UnexpectedException, FogbowRasException {
        Token token = this.mapperPlugin.map(federationUserToken);
        return this.securityRulePlugin.getSecurityRules(order, token);
    }

    @Override
    public String requestSecurityRule(Order order, SecurityRule securityRule,
                                      FederationUserToken federationUserToken) throws UnexpectedException, FogbowRasException {
        Token token = this.mapperPlugin.map(federationUserToken);
        return this.securityRulePlugin.requestSecurityRule(securityRule, order, token);
    }

    @Override
    public void deleteSecurityRule(String securityRuleId, FederationUserToken federationUserToken) throws Exception {
        Token token = this.mapperPlugin.map(federationUserToken);
        this.securityRulePlugin.deleteSecurityRule(securityRuleId, token);
    }

    private Instance getResourceInstance(Order order, ResourceType resourceType, Token token)
            throws FogbowRasException, UnexpectedException {
        Instance instance;
        String instanceId = order.getInstanceId();
        switch (resourceType) {
            case COMPUTE:
                instance = this.computePlugin.getInstance(instanceId, token);
                instance = this.getFullComputeInstance(((ComputeOrder) order), ((ComputeInstance) instance));
                break;
            case NETWORK:
                instance = this.networkPlugin.getInstance(instanceId, token);
                break;
            case VOLUME:
                instance = this.volumePlugin.getInstance(instanceId, token);
                break;
            case ATTACHMENT:
                instance = this.attachmentPlugin.getInstance(instanceId, token);
                instance = this.getFullAttachmentInstance(((AttachmentOrder) order), ((AttachmentInstance) instance));
                break;
            case PUBLIC_IP:
                instance = this.publicIpPlugin.getInstance(instanceId, token);
                instance = this.getFullPublicIpInstance(((PublicIpOrder) order), ((PublicIpInstance) instance));
                break;
            default:
                throw new UnexpectedException(String.format(Messages.Exception.UNSUPPORTED_REQUEST_TYPE, order.getType()));
        }
        order.setCachedInstanceState(instance.getState());
        instance.setProvider(order.getProvider());
        return instance;
    }

    private InstanceState getInstanceStateBasedOnOrderState(Order order) {
        InstanceState instanceState = null;
        // If order state is DEACTIVATED or CLOSED, an exception is throw before method call.
        // If order state is FULFILLED or SPAWNING, the order has an instance id, so this method is never called.
        if (order.getOrderState().equals(OrderState.OPEN) || order.getOrderState().equals(OrderState.PENDING)) {
            instanceState = InstanceState.DISPATCHED;
        } else if (order.getOrderState().equals(OrderState.FAILED_AFTER_SUCCESSUL_REQUEST) ||
                order.getOrderState().equals(OrderState.FAILED_ON_REQUEST)) {
            instanceState = InstanceState.FAILED;
        }
        return instanceState;
    }

    /**
     * protected visibility for tests
     */
    protected List<String> getNetworkInstanceIdsFromNetworkOrderIds(ComputeOrder order) throws InvalidParameterException {
        List<String> networkOrdersId = order.getNetworkIds();
        List<String> networkInstanceIDs = new LinkedList<String>();

        String computeOrderUserId = order.getFederationUserToken().getUserId();

        for (String orderId : networkOrdersId) {
            Order networkOrder = SharedOrderHolders.getInstance().getActiveOrdersMap().get(orderId);

            if (networkOrder == null) {
                throw new InvalidParameterException(Messages.Exception.INVALID_PARAMETER);
            } else {
                String networkOrderUserId = networkOrder.getFederationUserToken().getUserId();
                if (!networkOrderUserId.equals(computeOrderUserId)) {
                    throw new InvalidParameterException(Messages.Exception.TRYING_TO_USE_RESOURCES_FROM_ANOTHER_USER);
                }
            }

            String instanceId = networkOrder.getInstanceId();
            networkInstanceIDs.add(instanceId);
        }
        return networkInstanceIDs;
    }

    protected ComputeInstance getFullComputeInstance(ComputeOrder order, ComputeInstance instance)
            throws UnexpectedException, FogbowRasException {
        ComputeInstance fullInstance = instance;
        String imageId = order.getImageId();
        String imageName = getAllImages(order.getFederationUserToken()).get(imageId);
        String publicKey = order.getPublicKey();

        List<UserData> userData = order.getUserData();

        // If no network ids were informed by the user, the default network is used and the compute is attached
        // to this network. The plugin has already added this information to the instance. Otherwise, the information
        // added by the plugin needs to be overwritten, since a compute instance is either attached to the networks
        // informed by the user in the request, or to default network (if no networks are informed).
        Map<String, String> computeNetworks = getNetworkOrderIdsFromComputeOrder(order);
        if (!computeNetworks.isEmpty()) {
            fullInstance.setNetworks(computeNetworks);
        }

        fullInstance.setImageId(imageId + " : " + imageName);
        fullInstance.setPublicKey(publicKey);
        fullInstance.setUserData(userData);

        return fullInstance;
    }

    protected AttachmentInstance getFullAttachmentInstance(AttachmentOrder order, AttachmentInstance instance) {
        AttachmentInstance fullInstance = instance;
        String savedVolumeId = order.getVolumeId();
        String savedComputeId = order.getComputeId();
        ComputeOrder computeOrder = (ComputeOrder) SharedOrderHolders.getInstance().getActiveOrdersMap().get(savedComputeId);
        VolumeOrder volumeOrder = (VolumeOrder) SharedOrderHolders.getInstance().getActiveOrdersMap().get(savedVolumeId);

        fullInstance.setComputeName(computeOrder.getName());
        fullInstance.setComputeId(computeOrder.getId());
        fullInstance.setVolumeName(volumeOrder.getName());
        fullInstance.setVolumeId(volumeOrder.getId());

        return fullInstance;
    }

    protected PublicIpInstance getFullPublicIpInstance(PublicIpOrder order, PublicIpInstance instance) {
        PublicIpInstance publicIpInstance = instance;
        String computeOrderId = order.getComputeOrderId();

        ComputeOrder retrievedComputeOrder = (ComputeOrder) SharedOrderHolders.getInstance().getActiveOrdersMap()
                .get(computeOrderId);

        String computeInstanceName = retrievedComputeOrder.getName();
        String computeInstanceId = retrievedComputeOrder.getId();
        publicIpInstance.setComputeName(computeInstanceName);
        publicIpInstance.setComputeId(computeInstanceId);

        return publicIpInstance;
    }

    protected Map<String, String> getNetworkOrderIdsFromComputeOrder(ComputeOrder order) {
        List<String> networkOrdersId = order.getNetworkIds();
        Map<String, String> computeNetworks = new HashMap<>();

        for (String orderId : networkOrdersId) {
            NetworkOrder networkOrder = (NetworkOrder) SharedOrderHolders.getInstance().getActiveOrdersMap().get(orderId);
            String networkId = networkOrder.getId();
            String networkName = networkOrder.getName();

            computeNetworks.put(networkId, networkName);
        }

        return computeNetworks;
    }

    // Used only in tests
    protected void setMapperPlugin(FederationToLocalMapperPlugin mapperPlugin) {
        this.mapperPlugin = mapperPlugin;
    }

    protected void setPublicIpPlugin(PublicIpPlugin publicIpPlugin) {
        this.publicIpPlugin = publicIpPlugin;
    }

    protected void setAttachmentPlugin(AttachmentPlugin attachmentPlugin) {
        this.attachmentPlugin = attachmentPlugin;
    }

    protected void setComputePlugin(ComputePlugin computePlugin) {
        this.computePlugin = computePlugin;
    }

    protected void setComputeQuotaPlugin(ComputeQuotaPlugin computeQuotaPlugin) {
        this.computeQuotaPlugin = computeQuotaPlugin;
    }

    protected void setNetworkPlugin(NetworkPlugin networkPlugin) {
        this.networkPlugin = networkPlugin;
    }

    protected void setVolumePlugin(VolumePlugin volumePlugin) {
        this.volumePlugin = volumePlugin;
    }

    protected void setImagePlugin(ImagePlugin imagePlugin) {
        this.imagePlugin = imagePlugin;
    }

    protected void setGenericRequestPlugin(GenericRequestPlugin genericRequestPlugin) {
        this.genericRequestPlugin = genericRequestPlugin;
    }
}