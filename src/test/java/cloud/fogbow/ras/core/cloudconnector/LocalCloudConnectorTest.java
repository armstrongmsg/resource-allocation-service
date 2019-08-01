package cloud.fogbow.ras.core.cloudconnector;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.InstanceNotFoundException;
import cloud.fogbow.common.exceptions.UnexpectedException;
import cloud.fogbow.common.models.CloudUser;
import cloud.fogbow.common.models.SystemUser;
import cloud.fogbow.common.util.connectivity.FogbowGenericResponse;
import cloud.fogbow.ras.api.http.response.ImageInstance;
import cloud.fogbow.ras.api.http.response.ImageSummary;
import cloud.fogbow.ras.api.http.response.InstanceState;
import cloud.fogbow.ras.api.http.response.OrderInstance;
import cloud.fogbow.ras.api.http.response.SecurityRuleInstance;
import cloud.fogbow.ras.api.http.response.quotas.ComputeQuota;
import cloud.fogbow.ras.api.http.response.quotas.allocation.ComputeAllocation;
import cloud.fogbow.ras.api.parameters.SecurityRule;
import cloud.fogbow.ras.core.BaseUnitTests;
import cloud.fogbow.ras.core.SharedOrderHolders;
import cloud.fogbow.ras.core.models.ResourceType;
import cloud.fogbow.ras.core.models.orders.AttachmentOrder;
import cloud.fogbow.ras.core.models.orders.ComputeOrder;
import cloud.fogbow.ras.core.models.orders.NetworkOrder;
import cloud.fogbow.ras.core.models.orders.Order;
import cloud.fogbow.ras.core.models.orders.OrderState;
import cloud.fogbow.ras.core.models.orders.PublicIpOrder;
import cloud.fogbow.ras.core.models.orders.VolumeOrder;

public class LocalCloudConnectorTest extends BaseUnitTests {

    private static final String ANY_VALUE = "anything";
    private static final String FAKE_ORDER_ID = "fake-order-id";
    private static final String FAKE_IMAGE_NAME = "fake-imageInstance-name";
    
    private static final int INSTANCES_TOTAL = 2;
    private static final int INSTANCES_USED = 1;
    private static final int RAM_TOTAL = 2048;
    private static final int RAM_USED = 1024;
    private static final int VCPU_TOTAL = 8;
    private static final int VCPU_USED = 4;

    private Order<?> order;

    @Before
    public void setUp() throws FogbowException {
        // mocking databaseManager
        super.mockReadOrdersFromDataBase();

        // mocking resource plugins
        super.mockResourcePlugins();

        // mocking instances and the return of getID method
        super.mockAttachmentInstance();
        super.mockComputeInstance();
        super.mockImageInstance();
        super.mockNetworkInstance();
        super.mockSecurityRuleInstance();
        super.mockVolumeInstance();

        // mocking SystemToCloudMapperPlugin to return CloudUser
        super.systemToCloudMapperPluginMocked();
        
        // starting the object we want to test
        this.setupLocalCloudConnector();
    }
    
    // test case: Request a compute instance when the plugin returns a correct id
    @Test
    public void testRequestComputeInstance() throws FogbowException {
        // set up
        this.order = Mockito.mock(ComputeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.COMPUTE);
        Mockito.when(super.computePlugin.requestInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class)))
                .thenReturn(BaseUnitTests.FAKE_INSTANCE_ID);

        // exercise
        String returnedInstanceId = super.localCloudConnector.requestInstance(this.order);

        // verify
        Assert.assertEquals(BaseUnitTests.FAKE_INSTANCE_ID, returnedInstanceId);
        Mockito.verify(super.computePlugin, Mockito.times(1)).requestInstance(Mockito.any(ComputeOrder.class),
                Mockito.any(CloudUser.class));
        Mockito.verify(super.volumePlugin, Mockito.times(0)).requestInstance(Mockito.any(VolumeOrder.class),
                Mockito.any(CloudUser.class));
        Mockito.verify(super.attachmentPlugin, Mockito.times(0)).requestInstance(Mockito.any(AttachmentOrder.class),
                Mockito.any(CloudUser.class));
        Mockito.verify(super.networkPlugin, Mockito.times(0)).requestInstance(Mockito.any(NetworkOrder.class),
                Mockito.any(CloudUser.class));
    }

    // test case: Request an attachment instance Mockito.when the plugin returns a
    // correct id
    @Test
    public void testRequestAttachmentInstance() throws FogbowException {
        // set up
        ComputeOrder source = Mockito.mock(ComputeOrder.class);
        SharedOrderHolders.getInstance().getActiveOrdersMap().put(BaseUnitTests.FAKE_COMPUTE_ID, source);
        
        VolumeOrder target = Mockito.mock(VolumeOrder.class);
        SharedOrderHolders.getInstance().getActiveOrdersMap().put(BaseUnitTests.FAKE_VOLUME_ID, target);
        
        this.order = Mockito.mock(AttachmentOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.ATTACHMENT);
        Mockito.when(super.attachmentPlugin.requestInstance(Mockito.any(AttachmentOrder.class),
                Mockito.any(CloudUser.class))).thenReturn(BaseUnitTests.FAKE_INSTANCE_ID);
        
        Mockito.when(((AttachmentOrder) this.order).getComputeOrderId()).thenReturn(BaseUnitTests.FAKE_COMPUTE_ID);
        Mockito.when(((AttachmentOrder) this.order).getVolumeOrderId()).thenReturn(BaseUnitTests.FAKE_VOLUME_ID);

        // exercise
        String returnedInstanceId = super.localCloudConnector.requestInstance(order);

        // verify
        Assert.assertEquals(BaseUnitTests.FAKE_INSTANCE_ID, returnedInstanceId);
        Mockito.verify(super.attachmentPlugin, Mockito.times(1)).requestInstance(Mockito.any(AttachmentOrder.class),
                Mockito.any(CloudUser.class));
        Mockito.verify(super.computePlugin, Mockito.times(0)).requestInstance(Mockito.any(ComputeOrder.class),
                Mockito.any(CloudUser.class));
        Mockito.verify(super.volumePlugin, Mockito.times(0)).requestInstance(Mockito.any(VolumeOrder.class),
                Mockito.any(CloudUser.class));
        Mockito.verify(super.networkPlugin, Mockito.times(0)).requestInstance(Mockito.any(NetworkOrder.class),
                Mockito.any(CloudUser.class));
    }

    // test case: Request a volume instance Mockito.when the plugin returns a correct id
    @Test
    public void testRequestVolumeInstance() throws FogbowException {

        // set up
        this.order = Mockito.mock(VolumeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.VOLUME);
        Mockito.when(volumePlugin.requestInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class))).thenReturn(FAKE_INSTANCE_ID);

        //exercise
        String returnedInstanceId = this.localCloudConnector.requestInstance(order);

        // verify
        Assert.assertEquals(FAKE_INSTANCE_ID, returnedInstanceId);
        Mockito.verify(computePlugin, Mockito.times(0)).requestInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(computePlugin, Mockito.times(0)).requestInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(1)).requestInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(0)).requestInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(0)).requestInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class));
    }

    // test case: Request a network instance Mockito.when the plugin returns a correct id
    @Test
    public void testRequestNetworkInstance() throws FogbowException {

        // set up
        this.order = Mockito.mock(NetworkOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.NETWORK);
        Mockito.when(networkPlugin.requestInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class))).thenReturn(FAKE_INSTANCE_ID);

        //exercise
        String returnedInstanceId = this.localCloudConnector.requestInstance(order);

        // verify
        Assert.assertEquals(FAKE_INSTANCE_ID, returnedInstanceId);
        Mockito.verify(computePlugin, Mockito.times(0)).requestInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(0)).requestInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(0)).requestInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(1)).requestInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class));
    }

    // test case: If plugin returns a null instance id, the method requestInstance() must throw an exception
    @Test(expected = UnexpectedException.class)
    public void testExceptionNullComputeInstanceId() throws FogbowException {

        // set up
        this.order = Mockito.mock(ComputeOrder.class);
//        Mockito.when(this.order.getSystemUser()).thenReturn(this.systemUser);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.COMPUTE);
        Mockito.when(computePlugin.requestInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class))).thenReturn(null);

        // exercise
        this.localCloudConnector.requestInstance(order);
    }

    // test case: If plugin returns a null instance id, the method requestInstance() must throw an exception
    @Test(expected = UnexpectedException.class)
    public void testExceptionNullNetworkInstanceId() throws FogbowException {

        // set up
        this.order = Mockito.mock(NetworkOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.NETWORK);
        Mockito.when(networkPlugin.requestInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class))).thenReturn(null);

        // exercise
        this.localCloudConnector.requestInstance(order);
    }

    // test case: If plugin returns a null instance id, the method requestInstance() must throw an exception
    @Test(expected = UnexpectedException.class)
    public void testExceptionNullAttachmentInstanceId() throws FogbowException {
        // set up
        ComputeOrder source = Mockito.mock(ComputeOrder.class);
        VolumeOrder target = Mockito.mock(VolumeOrder.class);
//        Mockito.when(source.getSystemUser()).thenReturn(this.systemUser);
//        Mockito.when(source.getProvider()).thenReturn(BaseUnitTests.LOCAL_MEMBER_ID);
//        Mockito.when(target.getSystemUser()).thenReturn(this.systemUser);
//        Mockito.when(target.getProvider()).thenReturn(BaseUnitTests.LOCAL_MEMBER_ID);
        SharedOrderHolders.getInstance().getActiveOrdersMap().put(FAKE_COMPUTE_ID, source);
        SharedOrderHolders.getInstance().getActiveOrdersMap().put(FAKE_VOLUME_ID, target);
        this.order = Mockito.mock(AttachmentOrder.class);
//        Mockito.when(this.order.getSystemUser()).thenReturn(this.systemUser);
//        Mockito.when(this.order.getProvider()).thenReturn(BaseUnitTests.LOCAL_MEMBER_ID);
        Mockito.when(((AttachmentOrder) this.order).getComputeOrderId()).thenReturn(FAKE_COMPUTE_ID);
        Mockito.when(((AttachmentOrder) this.order).getVolumeOrderId()).thenReturn(FAKE_VOLUME_ID);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.ATTACHMENT);
        Mockito.when(attachmentPlugin.requestInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class))).thenReturn(null);

        // exercise
        this.localCloudConnector.requestInstance(order);

        // tear down
        SharedOrderHolders.getInstance().getActiveOrdersMap().clear();
    }

    // test case: If plugin returns a null instance id, the method requestInstance() must throw an exception
    @Test(expected = UnexpectedException.class)
    public void testExceptionNullVolumeInstanceId() throws FogbowException {

        // set up
        this.order = Mockito.mock(VolumeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.VOLUME);
        Mockito.when(volumePlugin.requestInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class))).thenReturn(null);

        // exercise
        this.localCloudConnector.requestInstance(order);
    }

    // test case: The order has an InstanceID, so the method getResourceInstance() is called.
    @Test
    public void testGetNetworkInstance() throws FogbowException {

        // set up
        this.order = Mockito.mock(NetworkOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.NETWORK);
        Mockito.when(this.order.getInstanceId()).thenReturn(FAKE_INSTANCE_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.FULFILLED);
        Mockito.when(networkPlugin.getInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class))).thenReturn(this.networkInstance);

        // exercise
        String returnedInstanceId = this.localCloudConnector.getInstance(order).getId();

        // verify
        Assert.assertEquals(FAKE_INSTANCE_ID, returnedInstanceId);
        Mockito.verify(computePlugin, Mockito.times(0)).getInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(0)).getInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(0)).getInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(1)).getInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class));
    }

    // test case: The order has an InstanceID, so the method getResourceInstance() is called.
    @Test
    public void testGetVolumeInstance() throws FogbowException {

        //set up
        this.order = Mockito.mock(VolumeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.VOLUME);
        Mockito.when(this.order.getInstanceId()).thenReturn(FAKE_INSTANCE_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.FULFILLED);
        Mockito.when(volumePlugin.getInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class))).thenReturn(this.volumeInstance);

        // exercise
        String returnedInstanceId = this.localCloudConnector.getInstance(order).getId();

        // verify
        Assert.assertEquals(FAKE_INSTANCE_ID, returnedInstanceId);
        Mockito.verify(computePlugin, Mockito.times(0)).getInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(1)).getInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(0)).getInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(0)).getInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class));
    }

    // test case: The order has an InstanceID, so the method getResourceInstance() is called.
    @Test
    public void testGetAttachmentInstance() throws FogbowException {
        // set up
        ComputeOrder compute = Mockito.mock(ComputeOrder.class);
        VolumeOrder volume = Mockito.mock(VolumeOrder.class);
        SharedOrderHolders.getInstance().getActiveOrdersMap().put(FAKE_COMPUTE_ID, compute);
        SharedOrderHolders.getInstance().getActiveOrdersMap().put(FAKE_VOLUME_ID, volume);
        this.order = Mockito.mock(AttachmentOrder.class);
        Mockito.when(((AttachmentOrder) this.order).getVolumeId()).thenReturn(FAKE_VOLUME_ID);
        Mockito.when(((AttachmentOrder) this.order).getComputeId()).thenReturn(FAKE_COMPUTE_ID);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.ATTACHMENT);
        Mockito.when(this.order.getInstanceId()).thenReturn(FAKE_INSTANCE_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.FULFILLED);
        Mockito.when(attachmentPlugin.getInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class)))
                .thenReturn(this.attachmentInstance);

        //exercise
        String returnedInstanceId = this.localCloudConnector.getInstance(order).getId();

        // verify
        Assert.assertEquals(FAKE_INSTANCE_ID, returnedInstanceId);
        Mockito.verify(computePlugin, Mockito.times(0)).getInstance(Mockito.any(ComputeOrder.class),
                Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(0)).getInstance(Mockito.any(VolumeOrder.class),
                Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(1)).getInstance(Mockito.any(AttachmentOrder.class),
                Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(0)).getInstance(Mockito.any(NetworkOrder.class),
                Mockito.any(CloudUser.class));

        // tear down
        SharedOrderHolders.getInstance().getActiveOrdersMap().clear();
    }

    // test case: The order has an InstanceID, so the method getResourceInstance() is called.
    @Test
    public void testGetComputeInstance() throws FogbowException {

        // set up

        // Avoid to test addReverseTunnelInfoMethod behaviour
        LocalCloudConnector localCloudConnectorSpy = Mockito.spy(this.localCloudConnector);

        this.order = Mockito.mock(ComputeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.COMPUTE);
        Mockito.when(this.order.getInstanceId()).thenReturn(FAKE_INSTANCE_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.FULFILLED);
        Mockito.when(computePlugin.getInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class))).thenReturn(this.computeInstance);

        // exercise
        String returnedInstanceId = localCloudConnectorSpy.getInstance(order).getId();

        // verify
        Assert.assertEquals(FAKE_INSTANCE_ID, returnedInstanceId);
        Mockito.verify(computePlugin, Mockito.times(1)).getInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(0)).getInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(0)).getInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(0)).getInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class));
    }

    // test case: If order instance is CLOSED, an exception must be throw
    @Test(expected = InstanceNotFoundException.class)
    public void testGetInstanceWithClosedOrder() throws FogbowException {

        // set up
        this.order = Mockito.mock(NetworkOrder.class);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.CLOSED);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.NETWORK);

        //exercise
        this.localCloudConnector.getInstance(order);
    }

    // test case: The order doesn't have an InstanceID, so an empty NetworkInstance is returned with the same id of order.
    // The order state is OPEN, so the instance state must be DISPATCHED.
    @Test
    public void testGetEmptyNetworkInstanceWithOpenOrder() throws FogbowException {

        // set up
        this.order = Mockito.mock(NetworkOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.NETWORK);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.OPEN);

        // exercise
        OrderInstance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.DISPATCHED, instance.getState());
        Mockito.verify(computePlugin, Mockito.times(0)).getInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(0)).getInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(0)).getInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(0)).getInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class));
    }

    // test case: The order doesn't have an InstanceID, so an empty NetworkInstance is returned with the same id of order.
    // The order state is PENDING, so the instance state must be DISPATCHED.
    @Test
    public void testGetEmptyNetworkInstanceWithPendingOrder() throws FogbowException {

        // set up
        this.order = Mockito.mock(NetworkOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.NETWORK);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.PENDING);

        // exercise
        OrderInstance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.DISPATCHED, instance.getState());
        Mockito.verify(computePlugin, Mockito.times(0)).getInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(0)).getInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(0)).getInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(0)).getInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class));
    }

    // test case: The order doesn't have an InstanceID, so an empty NetworkInstance is returned with the same id of order.
    // The order state is FAILED_AFTER_SUCCESSFUL_REQUEST, so the instance state must be FAILED_AFTER_SUCCESSFUL_REQUEST.
    @Test
    public void testGetEmptyNetworkInstanceWithFailedOrder() throws FogbowException {

        // set up
        this.order = Mockito.mock(NetworkOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.NETWORK);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.FAILED_AFTER_SUCCESSFUL_REQUEST);

        // exercise
        OrderInstance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.FAILED, instance.getState());
        Mockito.verify(computePlugin, Mockito.times(0)).getInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(0)).getInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(0)).getInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(0)).getInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class));
    }

    // test case: The order doesn't have an InstanceID, so an empty VolumeInstance is returned with the same id of order.
    // The order state is OPEN, so the instance state must be DISPATCHED.
    @Test
    public void testGetEmptyVolumeInstanceWithOpenOrder() throws FogbowException {

        // set up
        this.order = Mockito.mock(VolumeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.VOLUME);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.OPEN);

        // exercise
        OrderInstance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.DISPATCHED, instance.getState());
        Mockito.verify(computePlugin, Mockito.times(0)).getInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(0)).getInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(0)).getInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(0)).getInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class));
    }

    // test case: The order doesn't have an InstanceID, so an empty VolumeInstance is returned with the same id of order.
    // The order state is PENDING, so the instance state must be DISPATCHED.
    @Test
    public void testGetEmptyVolumeInstanceWithPendingOrder() throws FogbowException {

        //set up
        this.order = Mockito.mock(VolumeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.VOLUME);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.PENDING);

        // exercise
        OrderInstance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.DISPATCHED, instance.getState());
        Mockito.verify(computePlugin, Mockito.times(0)).getInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(0)).getInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(0)).getInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(0)).getInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class));
    }

    // test case: The order doesn't have an InstanceID, so an empty VolumeInstance is returned with the same id of order.
    // The order state is FAILED_AFTER_SUCCESSFUL_REQUEST, so the instance state must be FAILED_AFTER_SUCCESSFUL_REQUEST.
    @Test
    public void testGetEmptyVolumeInstanceWithFailedOrder() throws FogbowException {

        // set up
        this.order = Mockito.mock(VolumeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.VOLUME);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.FAILED_AFTER_SUCCESSFUL_REQUEST);

        // exercise
        OrderInstance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.FAILED, instance.getState());
        Mockito.verify(computePlugin, Mockito.times(0)).getInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(0)).getInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(0)).getInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(0)).getInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class));
    }

    // test case: The order doesn't have an InstanceID, so an empty AttachmentInstance is returned with the same id of order.
    // The order state is OPEN, so the instance state must be DISPATCHED.
    @Test
    public void testGetEmptyAttachmentInstanceWithOpenOrder() throws FogbowException {

        // set up
        this.order = Mockito.mock(AttachmentOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.ATTACHMENT);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.OPEN);

        // exercise
        OrderInstance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.DISPATCHED, instance.getState());
        Mockito.verify(computePlugin, Mockito.times(0)).getInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(0)).getInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(0)).getInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(0)).getInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class));
    }

    // test case: The order doesn't have an InstanceID, so an empty AttachmentInstance is returned with the same id of order.
    // The order state is PENDING, so the instance state must be DISPATCHED.
    @Test
    public void testGetEmptyAttachmentInstanceWithPendingOrder() throws FogbowException {

        // set up
        this.order = Mockito.mock(AttachmentOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.ATTACHMENT);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.PENDING);

        // exercise
        OrderInstance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.DISPATCHED, instance.getState());
        Mockito.verify(computePlugin, Mockito.times(0)).getInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(0)).getInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(0)).getInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(0)).getInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class));
    }

    // test case: The order doesn't have an InstanceID, so an empty AttachmentInstance is returned with the same id of order.
    // The order state is FAILED_AFTER_SUCCESSFUL_REQUEST, so the instance state must be FAILED_AFTER_SUCCESSFUL_REQUEST.
    @Test
    public void testGetEmptyAttachmentInstanceWithFailedOrder() throws FogbowException {

        // set up
        this.order = Mockito.mock(AttachmentOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.ATTACHMENT);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.FAILED_AFTER_SUCCESSFUL_REQUEST);

        // exercise
        OrderInstance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.FAILED, instance.getState());
        Mockito.verify(computePlugin, Mockito.times(0)).getInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(0)).getInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(0)).getInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(0)).getInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class));
    }

    // test case: The order doesn't have an InstanceID, so an empty ComputeInstance is returned with the same id of order.
    // The order state is OPEN, so the instance state must be DISPATCHED.
    @Test
    public void testGetEmptyComputeInstanceWithOpenOrder() throws FogbowException {

        // set up
        this.order = Mockito.mock(ComputeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.COMPUTE);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.OPEN);

        // exercise
        OrderInstance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.DISPATCHED, instance.getState());
        Mockito.verify(computePlugin, Mockito.times(0)).getInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(0)).getInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(0)).getInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(0)).getInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class));
    }

    // test case: The order doesn't have an InstanceID, so an empty ComputeInstance is returned with the same id of order.
    // The order state is PENDING, so the instance state must be DISPATCHED.
    @Test
    public void testGetEmptyComputeInstanceWithPendingOrder() throws FogbowException {

        // set up
        this.order = Mockito.mock(ComputeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.COMPUTE);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.PENDING);

        // exercise
        OrderInstance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.DISPATCHED, instance.getState());
        Mockito.verify(computePlugin, Mockito.times(0)).getInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(0)).getInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(0)).getInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(0)).getInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class));
    }

    // test case: The order doesn't have an InstanceID, so an empty ComputeInstance is returned with the same id of order.
    // The order state is FAILED_AFTER_SUCCESSFUL_REQUEST, so the instance state must be FAILED_AFTER_SUCCESSFUL_REQUEST.
    @Test
    public void testGetEmptyComputeInstanceWithFailedOrder() throws FogbowException {

        // set up
        this.order = Mockito.mock(ComputeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.COMPUTE);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.FAILED_AFTER_SUCCESSFUL_REQUEST);

        // exercise
        OrderInstance instance = this.localCloudConnector.getInstance(order);

        //verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.FAILED, instance.getState());
        Mockito.verify(computePlugin, Mockito.times(0)).getInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(0)).getInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(0)).getInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(0)).getInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class));
    }

    // test case: Try to delete an instance without instance id. Nothing happens
    @Test
    public void testDeleteInstanceWithoutInstanceID() throws FogbowException {

        // set up
        this.order = Mockito.mock(ComputeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.COMPUTE);

        // exercise
        this.localCloudConnector.deleteInstance(order);

        // verify
        Mockito.verify(computePlugin, Mockito.times(0)).deleteInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(0)).deleteInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(0)).deleteInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(0)).deleteInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class));
    }

    // test case: Deleting a compute instance with ID. Compute plugin must be called.
    @Test
    public void testDeleteComputeInstance() throws FogbowException {

        // set up
        this.order = Mockito.mock(ComputeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.COMPUTE);
        Mockito.when(this.order.getInstanceId()).thenReturn(FAKE_INSTANCE_ID);

        // exercise
        this.localCloudConnector.deleteInstance(order);

        // verify
        Mockito.verify(computePlugin, Mockito.times(1)).deleteInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(0)).deleteInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(0)).deleteInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(0)).deleteInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class));
    }

    // test case: Deleting a volume instance with ID. Volume plugin must be called.
    @Test
    public void testDeleteVolumeInstance() throws FogbowException {

        // set up
        this.order = Mockito.mock(VolumeOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.VOLUME);
        Mockito.when(this.order.getInstanceId()).thenReturn(FAKE_INSTANCE_ID);

        // exercise
        this.localCloudConnector.deleteInstance(order);

        // verify
        Mockito.verify(computePlugin, Mockito.times(0)).deleteInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(1)).deleteInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(0)).deleteInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(0)).deleteInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class));
    }

    // test case: Deleting a network instance with ID. Network plugin must be called.
    @Test
    public void testDeleteNetworkInstance() throws FogbowException {

        // set up
        this.order = Mockito.mock(NetworkOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.NETWORK);
        Mockito.when(this.order.getInstanceId()).thenReturn(FAKE_INSTANCE_ID);

        // exercise
        this.localCloudConnector.deleteInstance(order);

        // verify
        Mockito.verify(computePlugin, Mockito.times(0)).deleteInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(0)).deleteInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(0)).deleteInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(1)).deleteInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class));
    }

    // test case: Deleting a attachment instance with ID. Attachment plugin must be called.
    @Test
    public void testDeleteAttachmentInstance() throws FogbowException {

        // set up
        this.order = Mockito.mock(AttachmentOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.ATTACHMENT);
        Mockito.when(this.order.getInstanceId()).thenReturn(FAKE_INSTANCE_ID);

        // exercise
        this.localCloudConnector.deleteInstance(order);

        // verify
        Mockito.verify(computePlugin, Mockito.times(0)).deleteInstance(Mockito.any(ComputeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(volumePlugin, Mockito.times(0)).deleteInstance(Mockito.any(VolumeOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(attachmentPlugin, Mockito.times(1)).deleteInstance(Mockito.any(AttachmentOrder.class), Mockito.any(CloudUser.class));
        Mockito.verify(networkPlugin, Mockito.times(0)).deleteInstance(Mockito.any(NetworkOrder.class), Mockito.any(CloudUser.class));
    }

    // test case: Getting an imageInstance. Image plugin must be called
    @Test
    public void testGetImage() throws FogbowException {

        // set up
        Mockito.when(this.imagePlugin.getImage(Mockito.any(String.class), Mockito.any(CloudUser.class))).thenReturn(this.imageInstance);
        
        SystemUser systemUser = createSystemUser();

        // exercise
        String returnedImageId = this.localCloudConnector.getImage(FAKE_IMAGE_ID, systemUser).getId();

        // verify
        Assert.assertEquals(FAKE_IMAGE_ID, returnedImageId);
        Mockito.verify(imagePlugin, Mockito.times(1)).getImage(Mockito.any(String.class), Mockito.any(CloudUser.class));
    }

    // test case: Getting a null imageInstance. Image plugin must be called
    @Test
    public void testGetNullImage() throws FogbowException {

        // set up
        Mockito.when(this.imagePlugin.getImage(Mockito.any(String.class), Mockito.any(CloudUser.class))).thenReturn(null);
        
        SystemUser systemUser = createSystemUser();

        // exercise
        ImageInstance imageInstance = this.localCloudConnector.getImage(FAKE_IMAGE_ID, systemUser);

        // verify
        Assert.assertNull(imageInstance);
        Mockito.verify(imagePlugin, Mockito.times(1)).getImage(Mockito.any(String.class), Mockito.any(CloudUser.class));
    }


    // test case: Getting user compute quota. Compute quota plugin must be called.
    @Test
    public void testGetUserComputeQuota() throws FogbowException {

        // set up
        ComputeAllocation fakeTotalComputeAllocation = new ComputeAllocation(VCPU_TOTAL, RAM_TOTAL, INSTANCES_TOTAL);
        ComputeAllocation fakeUsedComputeAllocation = new ComputeAllocation(VCPU_USED, RAM_USED, INSTANCES_USED);
        ComputeQuota fakeComputeQuota = new ComputeQuota(fakeTotalComputeAllocation, fakeUsedComputeAllocation);
        Mockito.when(this.computeQuotaPlugin.getUserQuota(Mockito.any(CloudUser.class))).thenReturn(fakeComputeQuota);
        
        SystemUser systemUser = createSystemUser();

        // exercise
        ComputeQuota quota = (ComputeQuota) this.localCloudConnector.getUserQuota(systemUser, ResourceType.COMPUTE);

        // verify
        Assert.assertEquals(VCPU_TOTAL, quota.getTotalQuota().getvCPU());
        Assert.assertEquals(RAM_TOTAL, quota.getTotalQuota().getRam());
        Assert.assertEquals(INSTANCES_TOTAL, quota.getTotalQuota().getInstances());
        Assert.assertEquals(VCPU_USED, quota.getUsedQuota().getvCPU());
        Assert.assertEquals(RAM_USED, quota.getUsedQuota().getRam());
        Assert.assertEquals(INSTANCES_USED, quota.getUsedQuota().getInstances());
        Mockito.verify(computeQuotaPlugin, Mockito.times(1)).getUserQuota(Mockito.any(CloudUser.class));
    }

    // test case: If the instance type isn't of Compute type, an exception must be throw
    @Test(expected = UnexpectedException.class)
    public void testGetUserVolumeQuotaException() throws FogbowException {
        // set up
        SystemUser systemUser = createSystemUser();

        // exercise
        this.localCloudConnector.getUserQuota(systemUser, ResourceType.VOLUME);
    }

    // test case: If the instance type isn't of Compute type, an exception must be throw
    @Test(expected = UnexpectedException.class)
    public void testGetUserAttachmentQuotaException() throws FogbowException {
        // set up
        SystemUser systemUser = createSystemUser();

        // exercise
        this.localCloudConnector.getUserQuota(systemUser, ResourceType.ATTACHMENT);
    }

    // test case: If the instance type isn't of Compute type, an exception must be throw
    @Test(expected = UnexpectedException.class)
    public void testGetUserNetworkQuotaException() throws FogbowException {
        // set up
        SystemUser systemUser = createSystemUser();

        // exercise
        this.localCloudConnector.getUserQuota(systemUser, ResourceType.NETWORK);
    }

    // test case: Getting all images. Image plugin must be called
    @Test
    public void testGetAllImages() throws FogbowException {
        // set up
        List<ImageSummary> fakeImageSummaryList = new ArrayList<>();
        fakeImageSummaryList.add(new ImageSummary(FAKE_IMAGE_ID, FAKE_IMAGE_NAME));
        Mockito.when(this.imagePlugin.getAllImages(Mockito.any(CloudUser.class))).thenReturn(fakeImageSummaryList);
        
        SystemUser systemUser = createSystemUser();

        // exercise
        List<ImageSummary> returnedImages = this.localCloudConnector.getAllImages(systemUser);

        // verify
        Assert.assertEquals(FAKE_IMAGE_NAME, returnedImages.get(0).getName());
        Assert.assertEquals(1, returnedImages.size());
        Mockito.verify(imagePlugin, Mockito.times(1)).getAllImages(Mockito.any(CloudUser.class));
    }

    // test case: The return of getAllImages must be null. Image plugin must be called.
    @Test
    public void testGetAllImagesNullReturn() throws FogbowException {

        // set up
        Mockito.when(this.imagePlugin.getAllImages(Mockito.any(CloudUser.class))).thenReturn(null);
        
        SystemUser systemUser = createSystemUser();

        // exercise
        List<ImageSummary> returnedImages = this.localCloudConnector.getAllImages(systemUser);

        // verify
        Assert.assertNull(returnedImages);
        Mockito.verify(imagePlugin, Mockito.times(1)).getAllImages(Mockito.any(CloudUser.class));
    }

    // test case: Generic requests should map the systemUser to a cloudUser and
    // redirect the request to GenericRequestPlugin.
    @Test
    public void testGenericRequest() throws FogbowException {
        // set up
        SystemUser systemUser = createSystemUser();
        CloudUser tokenMock = Mockito.mock(CloudUser.class);
        Mockito.doReturn(tokenMock).when(mapperPlugin).map(Mockito.eq(systemUser));
        Mockito.doReturn(Mockito.mock(FogbowGenericResponse.class)).when(genericRequestPlugin)
                .redirectGenericRequest(Mockito.any(String.class), Mockito.eq(tokenMock));

        // exercise
        localCloudConnector.genericRequest(Mockito.anyString(), systemUser);

        // verify
        Mockito.verify(mapperPlugin, Mockito.times(1)).map(Mockito.any(SystemUser.class));
        Mockito.verify(genericRequestPlugin, Mockito.times(1)).redirectGenericRequest(Mockito.any(String.class),
                Mockito.eq(tokenMock));
    }
    
    // test case: The order doesn't have an InstanceID, so an empty PublicIpInstance
    // is returned with the same id of order. The order state is OPEN, so the
    // instance state must be DISPATCHED.
    @Test
    public void testGetEmptyPublicIpInstanceWithOpenOrder() throws FogbowException {
        // set up
        this.order = Mockito.mock(PublicIpOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.PUBLIC_IP);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.OPEN);

        // exercise
        OrderInstance instance = this.localCloudConnector.getInstance(order);

        // verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.DISPATCHED, instance.getState());
        Mockito.verify(publicIpPlugin, Mockito.times(0)).getInstance(Mockito.any(),
                Mockito.any(CloudUser.class));
    }
    
    // test case: The order doesn't have an InstanceID, so an empty PublicIpInstance
    // is returned with the same id of order. The order state is PENDING, so the
    // instance state must be DISPATCHED.
    @Test
    public void testGetEmptyPublicIpInstanceWithPendingOrder() throws FogbowException {
        // set up
        this.order = Mockito.mock(PublicIpOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.PUBLIC_IP);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.PENDING);

        // exercise
        OrderInstance instance = this.localCloudConnector.getInstance(order);

        // verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.DISPATCHED, instance.getState());
        Mockito.verify(publicIpPlugin, Mockito.times(0)).getInstance(Mockito.any(),
                Mockito.any(CloudUser.class));
    }
    
    // test case: The order doesn't have an InstanceID, so an empty PublicIpInstance
    // is returned with the same id of order. The order state is
    // FAILED_AFTER_SUCCESSFUL_REQUEST, so the instance state must be
    // FAILED_AFTER_SUCCESSFUL_REQUEST.
    @Test
    public void testGetEmptyPublicIpInstanceWithFailedOrder() throws FogbowException {
        // set up
        this.order = Mockito.mock(PublicIpOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.PUBLIC_IP);
        Mockito.when(this.order.getInstanceId()).thenReturn(null);
        Mockito.when(this.order.getId()).thenReturn(FAKE_ORDER_ID);
        Mockito.when(this.order.getOrderState()).thenReturn(OrderState.FAILED_AFTER_SUCCESSFUL_REQUEST);

        // exercise
        OrderInstance instance = this.localCloudConnector.getInstance(order);

        // verify
        Assert.assertEquals(FAKE_ORDER_ID, instance.getId());
        Assert.assertEquals(InstanceState.FAILED, instance.getState());
        Mockito.verify(publicIpPlugin, Mockito.times(0)).getInstance(Mockito.any(),
                Mockito.any(CloudUser.class));
    }
    
    // test case: When invoking the getInstance method of an order with a different
    // resource type than the COMPUTE, NETWORK, VOLUME, ATTACHMENT, and PUBLIC_IP,
    // an UnexpectedException will be thrown.
    @Test(expected = UnexpectedException.class) // verify
    public void testGetInstanceForAnUnsupportedResourceType() throws FogbowException {
        // set up
        this.order = Mockito.mock(PublicIpOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.GENERIC_RESOURCE);

        // exercise
        this.localCloudConnector.getInstance(order);
    }
    
    // test case: When calling the deleteInstance method, the public IP plug-in must
    // be called to exclude the public IP instance in the cloud from the instance ID
    // contained in the order passed by parameter.
    @Test
    public void testDeletePublicIpInstance() throws FogbowException {
        // set up
        this.order = Mockito.mock(PublicIpOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.PUBLIC_IP);
        Mockito.when(this.order.getInstanceId()).thenReturn(FAKE_INSTANCE_ID);

        // exercise
        this.localCloudConnector.deleteInstance(this.order);

        // verify
        Mockito.verify(this.publicIpPlugin, Mockito.times(1)).deleteInstance(Mockito.any(),
                Mockito.any(CloudUser.class));
    }
    
    // test case: When invoking the getAllImages method, the plugin should throw an
    // InstanceNotFoundException if it does not find the instance.
    @Test
    public void testDeleteInstanceThrowsInstanceNotFoundException() throws FogbowException {
        // set up
        this.order = Mockito.mock(PublicIpOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.PUBLIC_IP);
        Mockito.when(this.order.getInstanceId()).thenReturn(FAKE_INSTANCE_ID);

        Mockito.doThrow(InstanceNotFoundException.class).when(this.publicIpPlugin)
                .deleteInstance(Mockito.any(), Mockito.any(CloudUser.class));

        // exercise
        this.localCloudConnector.deleteInstance(order);

        // verify
        Mockito.verify(this.publicIpPlugin, Mockito.times(1)).deleteInstance(Mockito.any(),
                Mockito.any(CloudUser.class));
    }
    
    // test case: When invoking the deleteInstance method of an order with a different
    // resource type than the COMPUTE, NETWORK, VOLUME, ATTACHMENT, and PUBLIC_IP,
    // an UnexpectedException will be thrown.
    @Test(expected = UnexpectedException.class) // verify
    public void testDeleteInstanceForAnUnsupportedResourceType() throws FogbowException {
        // set up
        this.order = Mockito.mock(PublicIpOrder.class);
        Mockito.when(this.order.getType()).thenReturn(ResourceType.GENERIC_RESOURCE);

        // exercise
        this.localCloudConnector.deleteInstance(order);
    }
    
    // test case: When invoking the getAllImages method and the plugin throwing an
    // UnexpectedException, it must pass this exception.
    @Test(expected = FogbowException.class) // verify
    public void testGetAllImagesPassingAnExceptionThrown() throws FogbowException {
        // set up
        Mockito.when(this.imagePlugin.getAllImages(Mockito.any(CloudUser.class))).thenThrow(new UnexpectedException());
        
        SystemUser systemUser = createSystemUser();

        // exercise
        this.localCloudConnector.getAllImages(systemUser);
    }
    
    // test case: When invoking the getImage method and the plug-in throwing an
    // UnexpectedException, it must pass this exception.
    @Test(expected = FogbowException.class) // verify
    public void testGetImagePassingAnExceptionThrown() throws FogbowException {
        // set up
        Mockito.when(this.imagePlugin.getImage(Mockito.anyString(), Mockito.any(CloudUser.class)))
                .thenThrow(new UnexpectedException());
        
        SystemUser systemUser = createSystemUser();

        // exercise
        this.localCloudConnector.getImage(FAKE_IMAGE_ID, systemUser);
    }
    
    // test case: When invoking the genericRequest method and the plug-in throwing an
    // UnexpectedException, it must pass this exception.
    @Test(expected = FogbowException.class) // verify
    public void testGenericRequestPassingAnExceptionThrown() throws FogbowException {
        // set up
        Mockito.when(this.genericRequestPlugin.redirectGenericRequest(Mockito.anyString(), Mockito.any(CloudUser.class)))
                .thenThrow(new UnexpectedException());
        
        SystemUser systemUser = createSystemUser();

        // exercise
        this.localCloudConnector.genericRequest(ANY_VALUE, systemUser);
    }
    
    // test case: When invoking the getAllSecurityRules method and the plug-in throwing an
    // UnexpectedException, it must pass this exception.
    @Test(expected = FogbowException.class) // verify
    public void testGetAllSecurityRulesPassingAnExceptionThrown() throws FogbowException {
        // set up
        this.order = Mockito.mock(PublicIpOrder.class);
        
        Mockito.when(this.securityRulePlugin.getSecurityRules(Mockito.any(PublicIpOrder.class),
                Mockito.any(CloudUser.class))).thenThrow(new UnexpectedException());
        
        SystemUser systemUser = createSystemUser();

        // exercise
        this.localCloudConnector.getAllSecurityRules(this.order, systemUser);
    }
    
    // test case: When calling the getAllSecurityRules method, the Security Rule
    // plug-in must be called to list the Security Rule instances in the cloud.
    @Test
    public void testGetAllSecurityRules() throws FogbowException {
        // set up
        this.order = Mockito.mock(PublicIpOrder.class);

        List<SecurityRuleInstance> securityRulesList = new ArrayList<>();
        securityRulesList.add(this.securityRuleInstance);
        Mockito.when(this.securityRulePlugin.getSecurityRules(Mockito.any(PublicIpOrder.class),
                Mockito.any(CloudUser.class))).thenReturn(securityRulesList);
        
        SystemUser systemUser = createSystemUser();

        // exercise
        this.localCloudConnector.getAllSecurityRules(this.order, systemUser);

        // verify
        Mockito.verify(this.securityRulePlugin, Mockito.times(1)).getSecurityRules(Mockito.any(PublicIpOrder.class),
                Mockito.any(CloudUser.class));
    }
    
    // test case: When invoking the requestSecurityRule method and the plug-in throwing an
    // UnexpectedException, it must pass this exception.
    @Test(expected = FogbowException.class) // verify
    public void testRequestSecurityRulePassingAnExceptionThrown() throws FogbowException {
        // set up
        this.order = Mockito.mock(PublicIpOrder.class);
        SecurityRule securityRule = Mockito.mock(SecurityRule.class);

        Mockito.when(this.securityRulePlugin.requestSecurityRule(Mockito.eq(securityRule), Mockito.eq(this.order),
                Mockito.any(CloudUser.class))).thenThrow(new UnexpectedException());
        
        SystemUser systemUser = createSystemUser();

        // exercise
        this.localCloudConnector.requestSecurityRule(this.order, securityRule, systemUser);
    }
    
    // test case: When calling the requestSecurityRule method, the Security Rule
    // plug-in must be called to request a Security Rule instance in the cloud.
    @Test
    public void testRequestSecurityRule() throws FogbowException {
        // set up
        this.order = Mockito.mock(PublicIpOrder.class);
        SecurityRule securityRule = Mockito.mock(SecurityRule.class);

        Mockito.when(this.securityRulePlugin.requestSecurityRule(Mockito.eq(securityRule), Mockito.eq(this.order),
                Mockito.any(CloudUser.class))).thenReturn(FAKE_SECURITY_RULE_ID);
        
        SystemUser systemUser = createSystemUser();

        // exercise
        this.localCloudConnector.requestSecurityRule(this.order, securityRule, systemUser);

        // verify
        Mockito.verify(this.securityRulePlugin, Mockito.times(1)).requestSecurityRule(Mockito.eq(securityRule),
                Mockito.eq(this.order), Mockito.any(CloudUser.class));
    }
    
    // test case: When invoking the deleteSecurityRule method and the plug-in throwing an
    // UnexpectedException, it must pass this exception.
    @Test(expected = FogbowException.class) // verify
    public void testDeleteSecurityRulePassingAnExceptionThrown() throws FogbowException {
        // set up
        Mockito.doThrow(UnexpectedException.class).when(this.securityRulePlugin).deleteSecurityRule(Mockito.anyString(),
                Mockito.any(CloudUser.class));
        
        SystemUser systemUser = createSystemUser();

        // exercise
        this.localCloudConnector.deleteSecurityRule(FAKE_SECURITY_RULE_ID, systemUser);
    }
    
    // test case: When calling the deleteSecurityRule method, the Security Rule
    // plug-in must be called to exclude an instance of Security Rule in the cloud.
    @Test
    public void testDeleteSecurityRule() throws FogbowException {
        // set up
        SystemUser systemUser = createSystemUser();
        
        // exercise
        this.localCloudConnector.deleteSecurityRule(FAKE_SECURITY_RULE_ID, systemUser);

        // verify
        Mockito.verify(this.securityRulePlugin, Mockito.times(1)).deleteSecurityRule(Mockito.anyString(),
                Mockito.any(CloudUser.class));
    }
    
    private void setupLocalCloudConnector() {
        LocalCloudConnector localCloudConnector = new LocalCloudConnector(BaseUnitTests.DEFAULT_CLOUD_NAME);
        localCloudConnector.setAttachmentPlugin(super.attachmentPlugin);
        localCloudConnector.setComputePlugin(super.computePlugin);
        localCloudConnector.setComputeQuotaPlugin(super.computeQuotaPlugin);
        localCloudConnector.setImagePlugin(super.imagePlugin);
        localCloudConnector.setMapperPlugin(super.mapperPlugin);
        localCloudConnector.setNetworkPlugin(super.networkPlugin);
        localCloudConnector.setPublicIpPlugin(super.publicIpPlugin);
        localCloudConnector.setVolumePlugin(super.volumePlugin);
        localCloudConnector.setGenericRequestPlugin(super.genericRequestPlugin);
        localCloudConnector.setSecurityRulePlugin(super.securityRulePlugin);
        super.localCloudConnector = localCloudConnector;
    }

}
