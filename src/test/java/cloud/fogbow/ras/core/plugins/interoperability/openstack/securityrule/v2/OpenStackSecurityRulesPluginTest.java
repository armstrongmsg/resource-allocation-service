package cloud.fogbow.ras.core.plugins.interoperability.openstack.securityrule.v2;

import cloud.fogbow.common.constants.FogbowConstants;
import cloud.fogbow.common.constants.OpenStackConstants;
import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.models.FederationUser;
import cloud.fogbow.common.util.HomeDir;
import cloud.fogbow.ras.core.PropertiesHolder;
import cloud.fogbow.ras.core.constants.SystemConstants;
import cloud.fogbow.ras.core.models.securityrules.Direction;
import cloud.fogbow.ras.core.models.securityrules.EtherType;
import cloud.fogbow.ras.core.models.securityrules.Protocol;
import cloud.fogbow.ras.core.models.securityrules.SecurityRule;
import cloud.fogbow.ras.core.plugins.interoperability.openstack.OpenStackV3Token;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.BasicStatusLine;
import cloud.fogbow.ras.core.models.instances.NetworkInstance;
import cloud.fogbow.ras.core.models.NetworkAllocationMode;
import cloud.fogbow.ras.core.models.orders.NetworkOrder;
import cloud.fogbow.ras.util.connectivity.AuditableHttpRequestClient;
import org.json.JSONException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidParameterException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;

public class OpenStackSecurityRulesPluginTest {

    private static final String NETWORK_NEUTRONV2_URL_KEY = "openstack_neutron_v2_url";
    private static final String DEFAULT_NETWORK_URL = "http://localhost:0000";
    private static final String SECURITY_RULE_ID = "securityRuleId";
    private static final String SECURITY_GROUP_ID = "securityGroupId";

    private static final String FAKE_TOKEN_PROVIDER = "fake-token-provider";
    private static final String FAKE_TOKEN_VALUE = "fake-token-value";
    private static final String FAKE_USER_ID = "fake-user-id";
    private static final String FAKE_NAME = "fake-name";
    private static final String FAKE_CLOUD_NAME = "fake-cloud-name";
    private static final String FAKE_PROJECT_ID = "fake-project-id";
    private static final String FAKE_FEDERATION_TOKEN_VALUE = "federation-token-value";
    private static final String FAKE_USER_NAME = "fake-user-name";
    private static final String FAKE_MEMBER_ID = "fake-member-id";
    private static final String FAKE_GATEWAY = "fake-gateway";
    private static final String FAKE_ADDRESS = "fake-address";

    private static final String SUFFIX_ENDPOINT_SECURITY_GROUP_RULES = OpenStackSecurityRulePlugin.SUFFIX_ENDPOINT_SECURITY_GROUP_RULES  +
            OpenStackSecurityRulePlugin.QUERY_PREFIX;

    private OpenStackSecurityRulePlugin openStackSecurityRulePlugin;
    private OpenStackV3Token defaultLocalUserAttributes;
    private HttpClient client;
    private Properties properties;
    private AuditableHttpRequestClient auditableHttpRequestClient;

    @Before
    public void setUp() throws InvalidParameterException {
        PropertiesHolder propertiesHolder = PropertiesHolder.getInstance();
        this.properties = propertiesHolder.getProperties();
        this.properties.put(NETWORK_NEUTRONV2_URL_KEY, DEFAULT_NETWORK_URL);

        String confFilePath = HomeDir.getPath() + SystemConstants.CLOUDS_CONFIGURATION_DIRECTORY_NAME + File.separator
                + "default" + File.separator + SystemConstants.CLOUD_SPECIFICITY_CONF_FILE_NAME;

        this.openStackSecurityRulePlugin = Mockito.spy(new OpenStackSecurityRulePlugin(confFilePath));

        this.client = Mockito.mock(HttpClient.class);
        this.auditableHttpRequestClient = Mockito.spy(new AuditableHttpRequestClient(this.client));
        this.openStackSecurityRulePlugin.setClient(this.auditableHttpRequestClient);

        this.defaultLocalUserAttributes = new OpenStackV3Token(FAKE_TOKEN_PROVIDER, FAKE_USER_ID, FAKE_TOKEN_VALUE, FAKE_PROJECT_ID);
    }

    @After
    public void validate() {
        Mockito.validateMockitoUsage();
    }

    //test case: The http client must make only 1 request
    @Test
    public void testRequestSecurityRule() throws Exception {
        //set up
        // post network
        String createSecurityRuleResponse = new CreateSecurityRuleResponse(
                new CreateSecurityRuleResponse.SecurityRule(SECURITY_RULE_ID)).toJson();
        Mockito.doReturn(createSecurityRuleResponse).when(this.auditableHttpRequestClient)
                .doPostRequest(Mockito.endsWith(OpenStackSecurityRulePlugin.SUFFIX_ENDPOINT_SECURITY_GROUP_RULES),
                        Mockito.eq(this.defaultLocalUserAttributes), Mockito.anyString());

        Mockito.doReturn(null).when(this.openStackSecurityRulePlugin).
                getSecurityRulesFromJson(Mockito.anyString());
        Mockito.doReturn(SECURITY_GROUP_ID).when(this.openStackSecurityRulePlugin).
                retrieveSecurityGroupId(Mockito.anyString(), Mockito.any(OpenStackV3Token.class));
        SecurityRule securityRule = createEmptySecurityRule();
        NetworkOrder order = createNetworkOrder();

        //exercise
        this.openStackSecurityRulePlugin.requestSecurityRule(securityRule, order,
                this.defaultLocalUserAttributes);

        //verify
        Mockito.verify(this.auditableHttpRequestClient, Mockito.times(1)).doPostRequest(
                Mockito.endsWith(OpenStackSecurityRulePlugin.SUFFIX_ENDPOINT_SECURITY_GROUP_RULES),
                Mockito.eq(this.defaultLocalUserAttributes), Mockito.anyString());
    }

    //test case: Tests if an exception will be thrown in case that openstack raise an error in security rule request.
    @Test
    public void testRequestSecurityRuleNetworkError() throws Exception {
        //set up
        HttpResponse httpResponsePostNetwork = createHttpResponse("", HttpStatus.SC_BAD_REQUEST);
        Mockito.when(this.client.execute(Mockito.any(HttpUriRequest.class))).thenReturn(httpResponsePostNetwork);
        SecurityRule securityRule = createEmptySecurityRule();
        NetworkOrder order = createNetworkOrder();

        //exercise
        try {
            this.openStackSecurityRulePlugin.requestSecurityRule(securityRule, order,
                    this.defaultLocalUserAttributes);
            Assert.fail();
        } catch (FogbowException e) {
            // Throws an exception, as expected
        } catch (Exception e) {
            Assert.fail();
        }

        //verify
        Mockito.verify(this.client, Mockito.times(1)).execute(Mockito.any(HttpUriRequest.class));
    }

    //test case: Tests get security rule from json response
    @Test
    public void testGetSecurityRuleFromJson() throws Exception {
        //set up
        String id = "securityRuleId";
        String cidr = "0.0.0.0";
        int portFrom = 0;
        int portTo = 0;
        String direction = "egress";
        String etherType = "IPv4";
        String protocol = "tcp";
        NetworkOrder order = createNetworkOrder();

        // Generating security rule response string
        JSONObject securityRuleContentJsonObject = generateJsonResponseForSecurityRules(id, cidr, portFrom, portTo,
                direction, etherType, protocol);

        HttpResponse httpResponseGetSecurityRules = createHttpResponse(securityRuleContentJsonObject.toString(), HttpStatus.SC_OK);
        Mockito.when(this.client.execute(Mockito.any(HttpUriRequest.class))).thenReturn(httpResponseGetSecurityRules);
        Mockito.doReturn(SECURITY_GROUP_ID).when(this.openStackSecurityRulePlugin).
                retrieveSecurityGroupId(Mockito.anyString(), Mockito.any(OpenStackV3Token.class));

        //exercise
        List<SecurityRule> securityRules = this.openStackSecurityRulePlugin.getSecurityRules(order,
                this.defaultLocalUserAttributes);
        SecurityRule securityRule = securityRules.get(0);

        //verify
        Assert.assertEquals(id, securityRule.getInstanceId());
        Assert.assertEquals(cidr, securityRule.getCidr());
        Assert.assertEquals(portFrom, securityRule.getPortFrom());
        Assert.assertEquals(portTo, securityRule.getPortTo());
        Assert.assertEquals(direction, securityRule.getDirection().toString());
        Assert.assertEquals(etherType, securityRule.getEtherType().toString());
        Assert.assertEquals(protocol, securityRule.getProtocol().toString());
    }

    //test case: Tests remove security rule
    @Test
    public void testRemoveInstance() throws IOException, JSONException, FogbowException {
        //set up
        String suffixEndpointSecurityRules = OpenStackSecurityRulePlugin.SUFFIX_ENDPOINT_SECURITY_GROUP_RULES + "/" +
                SECURITY_RULE_ID;

        Mockito.doNothing().when(this.auditableHttpRequestClient).doDeleteRequest(
                Mockito.endsWith(suffixEndpointSecurityRules), Mockito.eq(this.defaultLocalUserAttributes));

        //exercise
        this.openStackSecurityRulePlugin.deleteSecurityRule(SECURITY_RULE_ID, this.defaultLocalUserAttributes);

        //verify
        Mockito.verify(this.auditableHttpRequestClient, Mockito.times(1)).doDeleteRequest(
                Mockito.endsWith(suffixEndpointSecurityRules), Mockito.eq(this.defaultLocalUserAttributes));
    }

    @Test
    public void testRetrieveSecurityGroupId() {
        
    }

    private SecurityRule createEmptySecurityRule() {
        return new SecurityRule(Direction.OUT, 0, 0, "0.0.0.0/0 ", EtherType.IPv4, Protocol.TCP);
    }

    private HttpResponse createHttpResponse(String content, int httpStatus) throws IOException {
        HttpResponse httpResponse = Mockito.mock(HttpResponse.class);
        HttpEntity httpEntity = Mockito.mock(HttpEntity.class);
        InputStream inputStrem = new ByteArrayInputStream(content.getBytes(UTF_8));

        Mockito.when(httpEntity.getContent()).thenReturn(inputStrem);
        Mockito.when(httpResponse.getEntity()).thenReturn(httpEntity);

        StatusLine statusLine = new BasicStatusLine(new ProtocolVersion("", 0, 0), httpStatus, "");
        Mockito.when(httpResponse.getStatusLine()).thenReturn(statusLine);

        return httpResponse;
    }

    private NetworkOrder createNetworkOrder() throws Exception {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(FogbowConstants.PROVIDER_ID_KEY, FAKE_TOKEN_PROVIDER);
        attributes.put(FogbowConstants.USER_ID_KEY, FAKE_USER_ID);
        attributes.put(FogbowConstants.USER_NAME_KEY, FAKE_USER_NAME);
        attributes.put(FogbowConstants.TOKEN_VALUE_KEY, FAKE_FEDERATION_TOKEN_VALUE);
        FederationUser federationUser = new FederationUser(attributes);

        NetworkOrder order = new NetworkOrder(federationUser, FAKE_MEMBER_ID, FAKE_MEMBER_ID, FAKE_CLOUD_NAME,
                FAKE_NAME, FAKE_GATEWAY, FAKE_ADDRESS, NetworkAllocationMode.STATIC);

        NetworkInstance networtkInstanceExcepted = new NetworkInstance(order.getId());
        order.setInstanceId(networtkInstanceExcepted.getId());
        return order;
    }

    private JSONObject generateJsonResponseForSecurityRules(String securityGroupId, String cidr, int portFrom, int portTo,
                                                            String direction, String etherType, String protocol) {
        JSONObject securityRuleContentJsonObject = new JSONObject();

        securityRuleContentJsonObject.put(OpenStackConstants.Network.ID_KEY_JSON, securityGroupId);
        securityRuleContentJsonObject.put(OpenStackConstants.Network.REMOTE_IP_PREFIX_KEY_JSON, cidr);
        securityRuleContentJsonObject.put(OpenStackConstants.Network.MAX_PORT_KEY_JSON, portTo);
        securityRuleContentJsonObject.put(OpenStackConstants.Network.MIN_PORT_KEY_JSON, portFrom);
        securityRuleContentJsonObject.put(OpenStackConstants.Network.DIRECTION_KEY_JSON, direction);
        securityRuleContentJsonObject.put(OpenStackConstants.Network.ETHER_TYPE_KEY_JSON, etherType);
        securityRuleContentJsonObject.put(OpenStackConstants.Network.PROTOCOL_KEY_JSON, protocol);

        JSONArray securityRulesJsonArray = new JSONArray();
        securityRulesJsonArray.add(securityRuleContentJsonObject);

        JSONObject securityRulesContentJsonObject = new JSONObject();
        securityRulesContentJsonObject.put(OpenStackConstants.Network.SECURITY_GROUP_RULES_KEY_JSON,
                securityRulesJsonArray);

        return securityRulesContentJsonObject;
    }
}
