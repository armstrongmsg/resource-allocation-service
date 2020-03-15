package cloud.fogbow.ras.core.plugins.interoperability.azure.network;

import cloud.fogbow.common.constants.AzureConstants;
import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.models.AzureUser;
import cloud.fogbow.common.util.HomeDir;
import cloud.fogbow.common.util.PropertiesUtil;
import cloud.fogbow.ras.api.http.response.NetworkInstance;
import cloud.fogbow.ras.constants.SystemConstants;
import cloud.fogbow.ras.core.TestUtils;
import cloud.fogbow.ras.core.models.NetworkAllocationMode;
import cloud.fogbow.ras.core.models.orders.NetworkOrder;
import cloud.fogbow.ras.core.plugins.interoperability.azure.AzureTestUtils;
import cloud.fogbow.ras.core.plugins.interoperability.azure.network.sdk.AzureVirtualNetworkOperationSDK;
import cloud.fogbow.ras.core.plugins.interoperability.azure.network.sdk.model.AzureCreateVirtualNetworkRef;
import cloud.fogbow.ras.core.plugins.interoperability.azure.network.sdk.model.AzureGetVirtualNetworkRef;
import cloud.fogbow.ras.core.plugins.interoperability.azure.util.AzureGeneralUtil;
import cloud.fogbow.ras.core.plugins.interoperability.azure.util.AzureStateMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

import java.io.File;
import java.util.Properties;

@PrepareForTest({AzureGeneralUtil.class})
public class AzureNetworkPluginTest extends AzureTestUtils {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private AzureUser azureUser;
    private String defaultResourceGroupName;
    private String defaultRegionName;
    private AzureNetworkPlugin azureNetworkPlugin;
    private AzureVirtualNetworkOperationSDK azureVirtualNetworkOperation;

    @Before
    public void setUp() {
        String azureConfFilePath = HomeDir.getPath() +
                SystemConstants.CLOUDS_CONFIGURATION_DIRECTORY_NAME + File.separator
                + AzureTestUtils.AZURE_CLOUD_NAME + File.separator
                + SystemConstants.CLOUD_SPECIFICITY_CONF_FILE_NAME;
        Properties properties = PropertiesUtil.readProperties(azureConfFilePath);
        this.defaultResourceGroupName = properties.getProperty(AzureConstants.DEFAULT_RESOURCE_GROUP_NAME_KEY);
        this.defaultRegionName = properties.getProperty(AzureConstants.DEFAULT_REGION_NAME_KEY);
        this.azureNetworkPlugin = Mockito.spy(new AzureNetworkPlugin(azureConfFilePath));
        AzureVirtualNetworkOperationSDK azureVirtualNetworkOperationSDK =
                new AzureVirtualNetworkOperationSDK(this.defaultRegionName, this.defaultResourceGroupName);
        this.azureVirtualNetworkOperation = Mockito.spy(azureVirtualNetworkOperationSDK);
        this.azureNetworkPlugin.setAzureVirtualNetworkOperationSDK(this.azureVirtualNetworkOperation);
        this.azureUser = AzureTestUtils.createAzureUser();
    }

    // test case: When calling the isReady method and the instance state is ready,
    // it must verify if It returns true value.
    @Test
    public void testIsReadySuccessfullyWhenIsReady() {
        // set up
        String instanceState = AzureStateMapper.SUCCEEDED_STATE;

        // exercise and verify
        Assert.assertTrue(this.azureNetworkPlugin.isReady(instanceState));
    }

    // test case: When calling the isReady method and the instance state is not ready,
    // it must verify if It returns false value.
    @Test
    public void testIsReadySuccessfullyWhenNotReady() {
        // set up
        String instanceState = TestUtils.ANY_VALUE;

        // exercise and verify
        Assert.assertFalse(this.azureNetworkPlugin.isReady(instanceState));
    }

    // test case: When calling the hasFailed method with any value,
    // it must verify if It returns false.
    @Test
    public void testHasFailedSuccessfullyWhenIsFailed() {
        // set up
        String instanceState = TestUtils.ANY_VALUE;

        // exercise and verify
        Assert.assertFalse(this.azureNetworkPlugin.hasFailed(instanceState));
    }

    // test case: When calling the requestInstance method with mocked methods,
    // it must verify if it creates all variable correct.
    @Test
    public void testRequestInstanceSuccessfully() throws FogbowException {
        // set up
        String cidr = "10.10.10.10/24";
        String name = "name";
        String orderId = "orderId";
        NetworkOrder networkOrder = Mockito.mock(NetworkOrder.class);
        Mockito.when(networkOrder.getCidr()).thenReturn(cidr);
        Mockito.when(networkOrder.getId()).thenReturn(orderId);

        PowerMockito.mockStatic(AzureGeneralUtil.class);
        PowerMockito.when(AzureGeneralUtil.generateResourceName()).thenReturn(name);

        Mockito.doNothing().when(this.azureVirtualNetworkOperation).doCreateInstance(Mockito.any(), Mockito.any());

        AzureCreateVirtualNetworkRef azureCreateVirtualNetworkRefExpected = AzureCreateVirtualNetworkRef.builder()
                .name(name)
                .cidr(cidr)
                .checkAndBuild();
        String instanceIdExpected = AzureGeneralUtil.defineInstanceId(name);

        // exercise
        String instanceId = this.azureNetworkPlugin.requestInstance(networkOrder, this.azureUser);

        // verify
        Mockito.verify(this.azureVirtualNetworkOperation, Mockito.times(TestUtils.RUN_ONCE)).doCreateInstance(
                Mockito.eq(azureCreateVirtualNetworkRefExpected), Mockito.eq(this.azureUser));
        Assert.assertEquals(instanceIdExpected, instanceId);
    }

    // test case: When calling the requestInstance method with mocked methods and throws an exception,
    // it must verify if it rethrows the same exception.
    @Test
    public void testRequestInstanceFail() throws FogbowException {
        // set up
        String cidr = "10.10.10.10/24";
        String name = "name";
        String orderId = "orderId";
        NetworkOrder networkOrder = Mockito.mock(NetworkOrder.class);
        Mockito.when(networkOrder.getCidr()).thenReturn(cidr);
        Mockito.when(networkOrder.getId()).thenReturn(orderId);
        Mockito.when(networkOrder.getName()).thenReturn(name);

        FogbowException exceptionExpected = new FogbowException(TestUtils.ANY_VALUE);
        Mockito.doThrow(exceptionExpected)
                .when(this.azureVirtualNetworkOperation).doCreateInstance(Mockito.any(), Mockito.any());

        // verify
        this.expectedException.expect(exceptionExpected.getClass());
        this.expectedException.expectMessage(exceptionExpected.getMessage());

        // exercise
        this.azureNetworkPlugin.requestInstance(networkOrder, this.azureUser);
    }

    // test case: When calling the buildNetworkInstance method,
    // it must verify if it returns the right networkInstance.
    @Test
    public void testBuildNetworkInstanceSuccessfully() {
        // set up
        String cirdExpected = "cirdExpected";
        String idExpected = "idExpected";
        String nameExpected = "nameExpected";
        String stateExpected = "stateExpected";

        // exercise
        AzureGetVirtualNetworkRef azureGetVirtualNetworkRef = AzureGetVirtualNetworkRef.builder()
                .cidr(cirdExpected)
                .id(idExpected)
                .name(nameExpected)
                .state(stateExpected)
                .build();
        NetworkInstance networkInstance = this.azureNetworkPlugin.buildNetworkInstance(azureGetVirtualNetworkRef);

        // verify
        Assert.assertEquals(cirdExpected, networkInstance.getCidr());
        Assert.assertEquals(idExpected, networkInstance.getId());
        Assert.assertEquals(stateExpected, networkInstance.getCloudState());
        Assert.assertEquals(nameExpected, networkInstance.getName());
        Assert.assertEquals(AzureGeneralUtil.NO_INFORMATION, networkInstance.getGateway());
        Assert.assertEquals(AzureGeneralUtil.NO_INFORMATION, networkInstance.getInterfaceState());
        Assert.assertEquals(AzureGeneralUtil.NO_INFORMATION, networkInstance.getMACInterface());
        Assert.assertEquals(NetworkAllocationMode.DYNAMIC, networkInstance.getAllocationMode());
    }

    // test case: When calling the getInstance method with mocked methods,
    // it must verify if it returns the right networkInstance.
    @Test
    public void testGetInstanceSuccessfully() throws FogbowException {
        // set up
        String instanceId = "instanceId";
        NetworkOrder networkOrder = Mockito.mock(NetworkOrder.class);
        Mockito.when(networkOrder.getInstanceId()).thenReturn(instanceId);

        String resourceNameExpected = AzureGeneralUtil.defineResourceName(instanceId);

        AzureGetVirtualNetworkRef azureGetVirtualNetworkRef = Mockito.mock(AzureGetVirtualNetworkRef.class);
        Mockito.doReturn(azureGetVirtualNetworkRef)
                .when(this.azureVirtualNetworkOperation).doGetInstance(
                        Mockito.eq(resourceNameExpected), Mockito.eq(this.azureUser));

        NetworkInstance networkInstanceExpected = Mockito.mock(NetworkInstance.class);
        Mockito.doReturn(networkInstanceExpected)
                .when(this.azureNetworkPlugin).buildNetworkInstance(Mockito.eq(azureGetVirtualNetworkRef));

        // exercise
        NetworkInstance networkInstance = this.azureNetworkPlugin.getInstance(networkOrder, this.azureUser);

        // verify
        Assert.assertEquals(networkInstanceExpected, networkInstance);
    }

    // test case: When calling the getInstance method with mocked methods and throws an exception,
    // it must verify if it rethrows the same exception.
    @Test
    public void testGetInstanceFail() throws FogbowException {
        // set up
        String instanceId = "instanceId";
        NetworkOrder networkOrder = Mockito.mock(NetworkOrder.class);
        Mockito.when(networkOrder.getInstanceId()).thenReturn(instanceId);
        String resourceNameExpected = AzureGeneralUtil.defineResourceName(instanceId);

        FogbowException exceptionExpected = new FogbowException(TestUtils.ANY_VALUE);
        Mockito.doThrow(exceptionExpected)
                .when(this.azureVirtualNetworkOperation).doGetInstance(
                Mockito.eq(resourceNameExpected), Mockito.eq(this.azureUser));

        // verify
        this.expectedException.expect(exceptionExpected.getClass());
        this.expectedException.expectMessage(exceptionExpected.getMessage());

        // exercise
        this.azureNetworkPlugin.getInstance(networkOrder, this.azureUser);
    }

}
