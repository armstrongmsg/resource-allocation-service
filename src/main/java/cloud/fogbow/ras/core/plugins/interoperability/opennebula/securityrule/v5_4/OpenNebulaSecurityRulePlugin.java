package cloud.fogbow.ras.core.plugins.interoperability.opennebula.securityrule.v5_4;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import cloud.fogbow.ras.api.parameters.SecurityRule;
import org.apache.log4j.Logger;
import org.opennebula.client.Client;
import org.opennebula.client.OneResponse;
import org.opennebula.client.secgroup.SecurityGroup;
import org.opennebula.client.vnet.VirtualNetwork;

import cloud.fogbow.common.exceptions.FatalErrorException;
import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.InstanceNotFoundException;
import cloud.fogbow.common.exceptions.InvalidParameterException;
import cloud.fogbow.common.models.CloudUser;
import cloud.fogbow.common.util.PropertiesUtil;
import cloud.fogbow.ras.api.http.response.SecurityRuleInstance;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.core.models.orders.Order;
import cloud.fogbow.ras.core.plugins.interoperability.SecurityRulePlugin;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.OpenNebulaClientUtil;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.OpenNebulaConfigurationPropertyKeys;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.securityrule.v5_4.SecurityGroupInfo.Template;

public class OpenNebulaSecurityRulePlugin implements SecurityRulePlugin<CloudUser> {

	public static final Logger LOGGER = Logger.getLogger(OpenNebulaSecurityRulePlugin.class);
	
	private static final String CIDR_SLICE = "[/]";
	private static final String INBOUND_TEMPLATE_VALUE = "inbound";
	private static final String OUTBOUND_TEMPLATE_VALUE = "outbound";
	private static final String RANGE_PORT_SEPARATOR = ":";

	private static final int BASE_VALUE = 2;
	private static final int IPV4_AMOUNT_BITS = 32;
	private static final int SLICE_POSITION_SECURITY_GROUP = 1;

	protected static final String OPENNEBULA_XML_ARRAY_SEPARATOR = ",";
	protected static final String TEMPLATE_VNET_SECURITY_GROUPS_PATH = "/VNET/TEMPLATE/SECURITY_GROUPS";

	private String endpoint;

	public OpenNebulaSecurityRulePlugin(String confFilePath) throws FatalErrorException {
		Properties properties = PropertiesUtil.readProperties(confFilePath);
		this.endpoint = properties.getProperty(OpenNebulaConfigurationPropertyKeys.OPENNEBULA_RPC_ENDPOINT_KEY);
	}
	
    @Override
    public String requestSecurityRule(SecurityRule securityRule, Order majorOrder, CloudUser cloudUser)
            throws FogbowException {

		LOGGER.info(String.format(Messages.Info.REQUESTING_INSTANCE, cloudUser.getToken()));
		Client client = OpenNebulaClientUtil.createClient(this.endpoint, cloudUser.getToken());

    	String virtualNetworkId = majorOrder.getInstanceId();
    	VirtualNetwork virtualNetwork = OpenNebulaClientUtil.getVirtualNetwork(client, virtualNetworkId);

		String securityGroupId = getSecurityGroupBy(virtualNetwork);		
		SecurityGroup securityGroup = OpenNebulaClientUtil.getSecurityGroup(client, securityGroupId); 
		
		String xml = securityGroup.info().getMessage();
		SecurityGroupInfo securityGroupInfo = SecurityGroupInfo.unmarshal(xml);
		
		Rule rule = createRuleBy(securityRule);
		rule.setSecurityGroupId(securityGroupId);
		
		String template = createSecurityGroupTemplate(securityGroupInfo, rule);
		OneResponse response = securityGroup.update(template);
		if (response.isError()) {
			LOGGER.error(String.format(Messages.Error.ERROR_WHILE_UPDATING_SECURITY_GROUPS, template));
			throw new InvalidParameterException();
		}
		String instanceId = rule.serialize();
		return instanceId;
    }

	@Override
    public List<SecurityRuleInstance> getSecurityRules(Order majorOrder, CloudUser cloudUser) throws FogbowException {
    	LOGGER.info(String.format(Messages.Info.GETTING_INSTANCE, majorOrder.getInstanceId(), cloudUser.getToken()));
    	Client client = OpenNebulaClientUtil.createClient(this.endpoint, cloudUser.getToken());

		// Both NetworkOrder's instanceId and PublicIdOrder's instanceId are network ids
		// in the opennebula context
		String virtualNetworkId = majorOrder.getInstanceId();
		VirtualNetwork virtualNetwork = OpenNebulaClientUtil.getVirtualNetwork(client, virtualNetworkId);
		
		String securityGroupId = getSecurityGroupBy(virtualNetwork);
		SecurityGroup securityGroup = OpenNebulaClientUtil.getSecurityGroup(client, securityGroupId);
		return getSecurityRules(securityGroup);
	}

    @Override
    public void deleteSecurityRule(String securityRuleId, CloudUser cloudUser) throws FogbowException {
        LOGGER.info(String.format(Messages.Info.DELETING_INSTANCE, securityRuleId, cloudUser.getToken()));
        Client client = OpenNebulaClientUtil.createClient(this.endpoint, cloudUser.getToken());

		Rule ruleToRemove = createRule(securityRuleId);
		String securityGroupId = ruleToRemove.getSecurityGroupId();
		SecurityGroup securityGroup = OpenNebulaClientUtil.getSecurityGroup(client, securityGroupId);
		SecurityGroupInfo securityGroupInfo = getSecurityGroupInfo(securityGroup);

		List<Rule> rules = getRules(securityGroupInfo);
		removeRule(ruleToRemove, rules);

		SecurityGroupTemplate securityGroupTemplate = createSecurityGroupTemplate(securityGroupInfo, rules);
		String xml = securityGroupTemplate.marshalTemplate();
		OneResponse response = securityGroup.update(xml);
		if (response.isError()) {
			String errorMsg = String.format(Messages.Error.ERROR_WHILE_REMOVING_SECURITY_RULE, securityGroupId,
					response.getMessage());
			LOGGER.error(errorMsg);
			throw new FogbowException(errorMsg);
		}
	}

	protected Rule createRuleBy(SecurityRule securityRule) {
		String protocol = securityRule.getProtocol().toString().toUpperCase();
		String slice[] = securityRule.getCidr().split(CIDR_SLICE);
		String ip = slice[0];
		int value = Integer.parseInt(slice[1]);
		String size = String.valueOf((int) Math.pow(BASE_VALUE, IPV4_AMOUNT_BITS - value));
		String range = securityRule.getPortFrom() + RANGE_PORT_SEPARATOR + securityRule.getPortTo();
		String type = mapRuleType(securityRule.getDirection());

		Rule rule = new Rule();
		rule.setProtocol(protocol);
		rule.setIp(ip);
		rule.setSize(size);
		rule.setRange(range);
		rule.setType(type);
		return rule;
	}

	private String createSecurityGroupTemplate(SecurityGroupInfo securityGroupInfo, Rule rule) {
		String id = securityGroupInfo.getId();
		String name = securityGroupInfo.getName();
		List<Rule> rules = securityGroupInfo.getTemplate().getRules();
		rules.add(rule);

		CreateSecurityGroupRequest request = new CreateSecurityGroupRequest.Builder()
				.id(id)
				.name(name)
				.rules(rules)
				.build();

		String template = request.getSecurityGroup().marshalTemplate();
		return template;
	}

	private String mapRuleType(SecurityRule.Direction direction) {
		String type = null;
		switch (direction) {
		case IN:
			type = INBOUND_TEMPLATE_VALUE;
			break;
		case OUT:
			type = OUTBOUND_TEMPLATE_VALUE;
			break;
		default:
			LOGGER.warn(String.format(Messages.Warn.INCONSISTENT_DIRECTION, direction));
			break;
		}
		return type;
	}    
    
	protected List<SecurityRuleInstance> getSecurityRules(SecurityGroup securityGroup) throws FogbowException {
		List<SecurityRuleInstance> securityRuleInstances = new ArrayList<SecurityRuleInstance>();
		try {
			SecurityGroupInfo securityGroupInfo = getSecurityGroupInfo(securityGroup);
			List<Rule> rules = getRules(securityGroupInfo);
			for (Rule rule : rules) {
				SecurityRule.Direction direction = rule.getDirection();
				int portFrom = rule.getPortFrom();
				int portTo = rule.getPortTo();
				String cidr = rule.getCIDR();
				SecurityRule.EtherType etherType = rule.getEtherType();
				SecurityRule.Protocol protocol = rule.getSRProtocol();
				rule.setSecurityGroupId(securityGroup.getId());
				SecurityRule securityRule = new SecurityRule(direction, portFrom, portTo, cidr, etherType, protocol);
				SecurityRuleInstance securityRuleInstance = new SecurityRuleInstance(rule.serialize(), securityRule);
				securityRuleInstances.add(securityRuleInstance);
			}
		} catch (Exception e) {
			LOGGER.error(String.format(Messages.Error.ERROR_WHILE_GETTING_SECURITY_RULES_INSTANCE), e);
			throw new FogbowException(e.getMessage(), e);
		}
		return securityRuleInstances;
	}

	protected List<Rule> getRules(SecurityGroupInfo securityGroupInfo) throws FogbowException {
    	try {
			Template template = securityGroupInfo.getTemplate();
			return template.getRules();
		} catch (Exception e) {
			throw new FogbowException(e.getMessage(), e);
		}
	}    
    
    /**
     * Note: In the Fogbow context, everytime the Virtual Network(VN) will have 
     * two security groups; The first is the default contained in every VN and
     * the second is the security group used by Fogbow because is created by own
     * 
     * @param  virtualNetwork : Opennebula client object regarding to Virtual Network
     * @return the correct security group associated with the Virtual Network
     */
    protected String getSecurityGroupBy(VirtualNetwork virtualNetwork) {
    	String securityGroupXMLContent = virtualNetwork.xpath(TEMPLATE_VNET_SECURITY_GROUPS_PATH);
    	if (securityGroupXMLContent == null || securityGroupXMLContent.isEmpty()) {
    		LOGGER.warn(Messages.Error.CONTENT_SECURITY_GROUP_NOT_DEFINED);
    		return null;
    	}
    	String[] securityGroupXMLContentSlices = securityGroupXMLContent.split(OPENNEBULA_XML_ARRAY_SEPARATOR);
    	if (securityGroupXMLContentSlices.length < 2) {
    		LOGGER.warn(Messages.Error.CONTENT_SECURITY_GROUP_WRONG_FORMAT);
    		return null;
    	}
    	return securityGroupXMLContentSlices[SLICE_POSITION_SECURITY_GROUP];
    }
    
	protected Rule createRule(String securityRuleId) throws FogbowException {
		try {
			return Rule.deserialize(securityRuleId);
		} catch (Exception e) {
			String errorMessage = String.format(Messages.Error.CONTENT_DESERIALIZATION_FAILURE, securityRuleId);
			throw new FogbowException(
					String.format(Messages.Error.ERROR_WHILE_REMOVING_SECURITY_RULE, securityRuleId, errorMessage));
		}
	}

	protected SecurityGroupTemplate createSecurityGroupTemplate(SecurityGroupInfo securityGroupInfo, List<Rule> rules)
			throws FogbowException {
		
		try {
			SecurityGroupTemplate securityGroupTemplate = new SecurityGroupTemplate();
			securityGroupTemplate.setId(securityGroupInfo.getId());
			securityGroupTemplate.setName(securityGroupInfo.getName());
			securityGroupTemplate.setRules(rules);
			return securityGroupTemplate;
		} catch (Exception e) {
			throw new FogbowException(e.getMessage(), e);
		}
	}

	protected SecurityGroupInfo getSecurityGroupInfo(SecurityGroup securityGroup) throws InstanceNotFoundException {
		String securityGroupXml = securityGroup.info().getMessage();
		SecurityGroupInfo securityGroupInfo = SecurityGroupInfo.unmarshal(securityGroupXml);
		if (securityGroupInfo == null) {
			String errorMsg = Messages.Exception.INSTANCE_NOT_FOUND;
			throw new InstanceNotFoundException(errorMsg);
		}
		return securityGroupInfo;
	}

	protected void removeRule(Rule ruleToRemove, List<Rule> rules) {
		if (rules == null) {
			return;
		}
		for (Rule rule: new ArrayList<>(rules)) {
			if (rule.equals(ruleToRemove)) {
				rules.remove(ruleToRemove); 
			}
		}
	}

}
