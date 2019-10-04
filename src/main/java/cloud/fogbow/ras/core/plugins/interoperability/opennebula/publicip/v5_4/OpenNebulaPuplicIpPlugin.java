package cloud.fogbow.ras.core.plugins.interoperability.opennebula.publicip.v5_4;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import org.apache.log4j.Logger;
import org.opennebula.client.Client;
import org.opennebula.client.OneResponse;
import org.opennebula.client.secgroup.SecurityGroup;
import org.opennebula.client.vm.VirtualMachine;
import org.opennebula.client.vnet.VirtualNetwork;

import cloud.fogbow.common.exceptions.FatalErrorException;
import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.InstanceNotFoundException;
import cloud.fogbow.common.exceptions.InvalidParameterException;
import cloud.fogbow.common.exceptions.UnauthorizedRequestException;
import cloud.fogbow.common.exceptions.UnexpectedException;
import cloud.fogbow.common.models.CloudUser;
import cloud.fogbow.common.util.PropertiesUtil;
import cloud.fogbow.ras.api.http.response.InstanceState;
import cloud.fogbow.ras.api.http.response.PublicIpInstance;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.constants.SystemConstants;
import cloud.fogbow.ras.core.models.ResourceType;
import cloud.fogbow.ras.core.models.orders.PublicIpOrder;
import cloud.fogbow.ras.core.plugins.interoperability.PublicIpPlugin;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.OpenNebulaClientUtil;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.OpenNebulaConfigurationPropertyKeys;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.OpenNebulaStateMapper;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.network.v5_4.CreateNetworkReserveRequest;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.network.v5_4.CreateNetworkUpdateRequest;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.securityrule.v5_4.CreateSecurityGroupRequest;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.securityrule.v5_4.Rule;

import javax.annotation.Nullable;

public class OpenNebulaPuplicIpPlugin implements PublicIpPlugin<CloudUser> {

	private static final Logger LOGGER = Logger.getLogger(OpenNebulaPuplicIpPlugin.class);

	private static final String ALL_PROTOCOLS = "ALL";
	private static final String INPUT_RULE_TYPE = "inbound";
	private static final String OUTPUT_RULE_TYPE = "outbound";
	private static final String SECURITY_GROUP_SEPARATOR = ",";
	private static final String PUBLIC_IP_RESOURCE = "Public IP";

	private static final int SIZE_ADDRESS_PUBLIC_IP = 1;
	private static final int ATTEMPTS_LIMIT_NUMBER = 5;

	protected static final String EXPRESSION_IP_FROM_NETWORK = "//NIC[NETWORK_ID = %s]/IP/text()";
	protected static final String EXPRESSION_NIC_ID_FROM_NETWORK = "//NIC[NETWORK_ID = %s]/NIC_ID/text()";
	protected static final String EXPRESSION_SECURITY_GROUPS_FROM_NIC_ID = "//NIC[NIC_ID = %s]/SECURITY_GROUPS/text()";
	protected static final String VNET_TEMPLATE_SECURITY_GROUPS_PATH = "/VNET/TEMPLATE/SECURITY_GROUPS";
	protected static final String POWEROFF_STATE = "POWEROFF";
	
	protected static final long ONE_POINT_TWO_SECONDS = 1200;
	protected static final boolean TERMINATE_HARD = true;

	private String endpoint;
	private String defaultPublicNetwork;

	public OpenNebulaPuplicIpPlugin(String confFilePath) throws FatalErrorException {
		Properties properties = PropertiesUtil.readProperties(confFilePath);
		this.endpoint = properties.getProperty(OpenNebulaConfigurationPropertyKeys.OPENNEBULA_RPC_ENDPOINT_KEY);
		this.defaultPublicNetwork = properties.getProperty(OpenNebulaConfigurationPropertyKeys.DEFAULT_PUBLIC_NETWORK_ID_KEY);
	}

	@Override
	public boolean isReady(String cloudState) {
		return OpenNebulaStateMapper.map(ResourceType.PUBLIC_IP, cloudState).equals(InstanceState.READY);
	}

	@Override
	public boolean hasFailed(String cloudState) {
		return OpenNebulaStateMapper.map(ResourceType.PUBLIC_IP, cloudState).equals(InstanceState.FAILED);
	}

	@Override
	public String requestInstance(PublicIpOrder publicIpOrder, CloudUser cloudUser) throws FogbowException {
		Client client = OpenNebulaClientUtil.createClient(this.endpoint, cloudUser.getToken());

		int defaultPublicNetworkId = this.convertToInteger(this.defaultPublicNetwork);
		int size = SIZE_ADDRESS_PUBLIC_IP;
		String name = SystemConstants.FOGBOW_INSTANCE_NAME_PREFIX + this.getRandomUUID();

		CreateNetworkReserveRequest reserveRequest = new CreateNetworkReserveRequest.Builder()
				.name(name)
				.size(size)
				.build();

		String publicIpInstanceId = this.doRequestInstance(client, defaultPublicNetworkId, reserveRequest);
		String securityGroupInstanceId = this.createSecurityGroup(client, publicIpOrder);

		this.addSecurityGroupToPublicIp(client, publicIpInstanceId, securityGroupInstanceId);
		this.attachPublicIpToCompute(client, publicIpInstanceId, publicIpOrder.getComputeOrderId());

		return publicIpInstanceId;
	}

	@Override
	public void deleteInstance(PublicIpOrder publicIpOrder, CloudUser cloudUser) throws FogbowException {
		Client client = OpenNebulaClientUtil.createClient(this.endpoint, cloudUser.getToken());
		String publicIpInstanceId = publicIpOrder.getInstanceId();

		// NOTE(pauloewerton): ONe does not allow deleting a resource associated to a VM, so we're using a workaround
		// by shutting down the VM, releasing network and secgroup resources, and then resuming it afterwards.
		VirtualMachine virtualMachine = OpenNebulaClientUtil.getVirtualMachine(client, publicIpOrder.getComputeId());
		virtualMachine.poweroff(TERMINATE_HARD);

		if (this.isPowerOff(virtualMachine)) {
			this.detachPublicIpFromCompute(virtualMachine, publicIpInstanceId);
			this.deleteSecurityGroup(client, publicIpInstanceId);
			this.doDeleteInstance(client, publicIpInstanceId);
			virtualMachine.resume();
		} else {
			String message = String.format(Messages.Error.ERROR_WHILE_REMOVING_RESOURCE, PUBLIC_IP_RESOURCE, publicIpInstanceId);
			throw new UnexpectedException(message);
		}
	}

	@Override
	public PublicIpInstance getInstance(PublicIpOrder publicIpOrder, CloudUser cloudUser) throws FogbowException {
		Client client = OpenNebulaClientUtil.createClient(this.endpoint, cloudUser.getToken());
		String publicIpInstanceId = publicIpOrder.getInstanceId();

		String publicIp = this.doGetInstance(client, publicIpInstanceId, publicIpOrder.getComputeId());

		PublicIpInstance publicIpInstance = new PublicIpInstance(
				publicIpInstanceId, OpenNebulaStateMapper.DEFAULT_READY_STATE, publicIp);

		return publicIpInstance;
	}

	protected String doRequestInstance(Client client, int defaultPublicNetworkId, CreateNetworkReserveRequest reserveRequest)
			throws InvalidParameterException {

		String publicNetworkReserveTemplate = reserveRequest.getVirtualNetworkReserved().marshalTemplate();
		return OpenNebulaClientUtil.reserveVirtualNetwork(client, defaultPublicNetworkId, publicNetworkReserveTemplate);
	}

	protected void doDeleteInstance(Client client, String virtualNetworkId)
			throws UnauthorizedRequestException, InstanceNotFoundException, InvalidParameterException, UnexpectedException {

		VirtualNetwork virtualNetwork = OpenNebulaClientUtil.getVirtualNetwork(client, virtualNetworkId);
		OneResponse response = virtualNetwork.delete();

		if (response.isError()) {
			throw new UnexpectedException(response.getErrorMessage());
		}
	}

	protected String doGetInstance(Client client, String publicIpInstanceId, String computeId)
			throws UnauthorizedRequestException, InstanceNotFoundException, InvalidParameterException {

		VirtualMachine virtualMachine = OpenNebulaClientUtil.getVirtualMachine(client, computeId);
		return virtualMachine.xpath(String.format(EXPRESSION_IP_FROM_NETWORK, publicIpInstanceId));
	}

	protected String createSecurityGroup(Client client, PublicIpOrder publicIpOrder) throws InvalidParameterException {
		String name = generateSecurityGroupName(publicIpOrder.getId());

		// "ALL" setting applies to all protocols if a port range is not defined
		String protocol = ALL_PROTOCOLS;

		// An undefined port range is interpreted by opennebula as all open
		String rangeAll = null;

		// An undefined ip and size is interpreted by opennebula as any network
		String ipAny = null;
		String sizeAny = null;

		// The virtualNetworkId and securityGroupId parameters are not used in this
		// context.
		String virtualNetworkId = null;
		String securityGroupId = null;

		Rule inputRule = new Rule(protocol, ipAny, sizeAny, rangeAll, INPUT_RULE_TYPE, virtualNetworkId,
				securityGroupId);
		Rule outputRule = new Rule(protocol, ipAny, sizeAny, rangeAll, OUTPUT_RULE_TYPE, virtualNetworkId,
				securityGroupId);

		List<Rule> rules = new ArrayList<>();
		rules.add(inputRule);
		rules.add(outputRule);

		CreateSecurityGroupRequest request = new CreateSecurityGroupRequest.Builder()
				.name(name)
				.rules(rules)
				.build();

		String template = request.getSecurityGroup().marshalTemplate();
		return OpenNebulaClientUtil.allocateSecurityGroup(client, template);
	}

	protected void addSecurityGroupToPublicIp(Client client, String publicIpInstanceId, String securityGroupInstanceId)
			throws InvalidParameterException {

		CreateNetworkUpdateRequest updateSecurityGroupsRequest = new CreateNetworkUpdateRequest.Builder()
				.securityGroups(securityGroupInstanceId)
				.build();

		int virtualNetworkId = this.convertToInteger(publicIpInstanceId);
		String publicNetworkUpdateTemplate = updateSecurityGroupsRequest.getVirtualNetworkUpdate().marshalTemplate();

		OpenNebulaClientUtil.updateVirtualNetwork(client, virtualNetworkId, publicNetworkUpdateTemplate);
	}

	protected void attachPublicIpToCompute(Client client, String publicIpId, String computeInstanceId)
			throws UnauthorizedRequestException, InstanceNotFoundException, InvalidParameterException {

		String template = this.createNicTemplate(publicIpId);
		VirtualMachine virtualMachine = OpenNebulaClientUtil.getVirtualMachine(client, computeInstanceId);
		OneResponse response = virtualMachine.nicAttach(template);

		if (response.isError()) {
			String message = String.format(Messages.Error.ERROR_WHILE_CREATING_NIC, template) + " " +
					String.format(Messages.Error.ERROR_MESSAGE, response.getMessage());
			throw new InvalidParameterException(message);
		}
	}

	protected String createNicTemplate(String virtualNetworkId) {
		CreateNicRequest request = new CreateNicRequest.Builder()
				.networkId(virtualNetworkId)
				.build();

		return request.getNic().marshalTemplate();
	}

	protected boolean isPowerOff(VirtualMachine virtualMachine) {
		String state;
		int count = 0;
		while (count < ATTEMPTS_LIMIT_NUMBER) {
			count++;
			this.waitMoment();
			virtualMachine.info();
			state = virtualMachine.stateStr();
			if (state.equalsIgnoreCase(POWEROFF_STATE)) {
				return true;
			}
		}
		return false;
	}

	protected void waitMoment() {
		try {
			Thread.sleep(ONE_POINT_TWO_SECONDS);
		} catch (InterruptedException e) {
			LOGGER.error(String.format(Messages.Error.ERROR_MESSAGE, e), e);
		}
	}

	protected void detachPublicIpFromCompute(VirtualMachine virtualMachine, String publicIpInstanceId)
			throws InvalidParameterException {

		String nicId = virtualMachine.xpath(String.format(EXPRESSION_NIC_ID_FROM_NETWORK, publicIpInstanceId));
		int id = convertToInteger(nicId);

		OneResponse response = virtualMachine.nicDetach(id);
		if (response.isError()) {
			String message = response.getErrorMessage();
			LOGGER.error(String.format(Messages.Error.ERROR_MESSAGE, message));
		}
	}

	protected void deleteSecurityGroup(Client client, String publicIpInstanceId)
			throws UnauthorizedRequestException, InvalidParameterException, InstanceNotFoundException, UnexpectedException {
		SecurityGroup securityGroup = this.getSecurityGroupForPublicIpNetwork(client, publicIpInstanceId);

		if (securityGroup == null) {
			throw new UnexpectedException();
		}

		OneResponse response = securityGroup.delete();
		if (response.isError()) {
			String message = response.getErrorMessage();
			LOGGER.error(String.format(Messages.Error.ERROR_MESSAGE, message));
		}
	}

	@Nullable
	protected SecurityGroup getSecurityGroupForPublicIpNetwork(Client client, String publicIpInstanceId)
			throws UnauthorizedRequestException, InstanceNotFoundException, InvalidParameterException {

		VirtualNetwork virtualNetwork = OpenNebulaClientUtil.getVirtualNetwork(client, publicIpInstanceId);
		String securityGroupIdsStr = virtualNetwork.xpath(VNET_TEMPLATE_SECURITY_GROUPS_PATH);

		if (securityGroupIdsStr == null || securityGroupIdsStr.isEmpty()) {
			LOGGER.warn(Messages.Error.CONTENT_SECURITY_GROUP_NOT_DEFINED);
			return null;
		}

		String[] securityGroupIds =  securityGroupIdsStr.split(SECURITY_GROUP_SEPARATOR);
		String securityGroupName = generateSecurityGroupName(publicIpInstanceId);
		for (String securityGroupId : securityGroupIds) {
			SecurityGroup securityGroup = OpenNebulaClientUtil.getSecurityGroup(client, securityGroupId);
			if (securityGroup.getName().equals(securityGroupName)) {
				return securityGroup;
			}
		}

		return null;
	}

	private static String generateSecurityGroupName(String orderId) {
		return SystemConstants.PIP_SECURITY_GROUP_PREFIX + orderId;
	}

	protected String getRandomUUID() {
		return UUID.randomUUID().toString();
	}

	protected int convertToInteger(String number) throws InvalidParameterException {
		try {
			return Integer.parseInt(number);
		} catch (NumberFormatException e) {
			LOGGER.error(String.format(Messages.Error.ERROR_WHILE_CONVERTING_TO_INTEGER), e);
			throw new InvalidParameterException();
		}
	}
}
