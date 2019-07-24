package cloud.fogbow.ras.core.processors;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.InstanceNotFoundException;
import cloud.fogbow.common.exceptions.UnavailableProviderException;
import cloud.fogbow.common.exceptions.UnexpectedException;
import cloud.fogbow.common.models.linkedlists.ChainedList;
import cloud.fogbow.ras.api.http.response.ComputeInstance;
import cloud.fogbow.ras.api.http.response.InstanceState;
import cloud.fogbow.ras.api.http.response.OrderInstance;
import cloud.fogbow.ras.constants.ConfigurationPropertyDefaults;
import cloud.fogbow.ras.core.BaseUnitTests;
import cloud.fogbow.ras.core.SharedOrderHolders;
import cloud.fogbow.ras.core.cloudconnector.CloudConnector;
import cloud.fogbow.ras.core.cloudconnector.CloudConnectorFactory;
import cloud.fogbow.ras.core.cloudconnector.LocalCloudConnector;
import cloud.fogbow.ras.core.models.orders.AttachmentOrder;
import cloud.fogbow.ras.core.models.orders.Order;
import cloud.fogbow.ras.core.models.orders.OrderState;
import cloud.fogbow.ras.core.models.orders.VolumeOrder;

@RunWith(PowerMockRunner.class)
@PrepareForTest(CloudConnectorFactory.class)
public class SpawningProcessorTest extends BaseUnitTests {

    private static final String DEFAULT_CLOUD_NAME = "default";
    private static final String FAKE_INSTANCE_ID = "fake-instance-id";
    private static final String FAKE_REMOTE_MEMBER_ID = "fake-intercomponent-member";

    private static final int DEFAULT_SLEEP_TIME = 500;
    private static final int SPAWNING_SLEEP_TIME = 2000;

    private ChainedList<Order> failedOrderList;
    private ChainedList<Order> fulfilledOrderList;
    private ChainedList<Order> openOrderList;
    private ChainedList<Order> spawningOrderList;
    private CloudConnector cloudConnector;
    private SpawningProcessor processor;
    private Thread thread;

    @Before
    public void setUp() throws UnexpectedException {
        super.mockReadOrdersFromDataBase();

        CloudConnectorFactory cloudConnectorFactory = Mockito.mock(CloudConnectorFactory.class);

        PowerMockito.mockStatic(CloudConnectorFactory.class);
        BDDMockito.given(CloudConnectorFactory.getInstance()).willReturn(cloudConnectorFactory);

        LocalCloudConnector localCloudConnector = Mockito.mock(LocalCloudConnector.class);
        Mockito.when(cloudConnectorFactory.getCloudConnector(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(localCloudConnector);

        this.cloudConnector = CloudConnectorFactory.getInstance()
                .getCloudConnector(BaseUnitTests.LOCAL_MEMBER_ID, DEFAULT_CLOUD_NAME);

        this.processor = Mockito.spy(new SpawningProcessor(BaseUnitTests.LOCAL_MEMBER_ID,
                ConfigurationPropertyDefaults.SPAWNING_ORDERS_SLEEP_TIME));

        SharedOrderHolders sharedOrderHolders = SharedOrderHolders.getInstance();
        this.spawningOrderList = sharedOrderHolders.getSpawningOrdersList();
        this.fulfilledOrderList = sharedOrderHolders.getFulfilledOrdersList();
        this.failedOrderList = sharedOrderHolders.getFailedAfterSuccessfulRequestOrdersList();
        this.openOrderList = sharedOrderHolders.getOpenOrdersList();

        this.thread = null;
    }

    @After
    public void tearDown() {
        if (this.thread != null) {
            this.thread.interrupt();
        }
        super.tearDown();
    }

    // test case: In calling the processSpawningOrder() method for any order other than spawning,
    // you must not make state transition by keeping the order in your source list.
    @Test
    public void testProcessComputeOrderNotSpawning() throws Exception {
        // set up
        Order order = createLocalOrder(getLocalMemberId());
        order.setInstanceId(FAKE_INSTANCE_ID);
        order.setOrderStateInTestMode(OrderState.OPEN);
        this.openOrderList.addItem(order);

        // exercise
        this.processor.processSpawningOrder(order);

        // verify
        Assert.assertEquals(order, this.openOrderList.getNext());
        Assert.assertNull(this.fulfilledOrderList.getNext());
    }

    // test case: When running thread in the SpawningProcessor and the OrderType is not a
    // Compute, the processSpawningOrder() method must immediately change the OrderState to
    // Fulfilled by adding in that list, and removed from the Spawning list.
    @Test
    public void testRunProcessWhenOrderTypeIsNetwork() throws Exception {
        // set up
        Order order = createLocalOrder(getLocalMemberId());
        order.setInstanceId(FAKE_INSTANCE_ID);
        order.setOrderStateInTestMode(OrderState.SPAWNING);
        this.spawningOrderList.addItem(order);
        Assert.assertNull(this.fulfilledOrderList.getNext());

        OrderInstance orderInstance = new ComputeInstance(FAKE_INSTANCE_ID);
        orderInstance.setReady();

        Mockito.doReturn(orderInstance).when(this.cloudConnector)
                .getInstance(Mockito.any(Order.class));

        // exercise
        this.thread = new Thread(this.processor);
        this.thread.start();
        Thread.sleep(DEFAULT_SLEEP_TIME);

        // verify
        Order test = this.fulfilledOrderList.getNext();
        Assert.assertNotNull(test);
        Assert.assertEquals(order.getInstanceId(), test.getInstanceId());
        Assert.assertEquals(OrderState.FULFILLED, test.getOrderState());
        Assert.assertNull(this.spawningOrderList.getNext());
    }

    // test case: When running thread in the SpawningProcessor and the OrderType is not a
    // Compute, the processSpawningOrder() method must immediately change the OrderState to
    // Fulfilled by adding in that list, and removed from the Spawning list.
    @Test
    public void testRunProcessWhenOrderTypeIsVolume() throws Exception {
        // set up
        Order order = new VolumeOrder();
        order.setRequester(BaseUnitTests.LOCAL_MEMBER_ID);
        order.setProvider(BaseUnitTests.LOCAL_MEMBER_ID);
        order.setOrderStateInTestMode(OrderState.SPAWNING);
        this.spawningOrderList.addItem(order);
        Assert.assertNull(this.fulfilledOrderList.getNext());

        OrderInstance orderInstance = new ComputeInstance(FAKE_INSTANCE_ID);
        orderInstance.setReady();

        Mockito.doReturn(orderInstance).when(this.cloudConnector)
                .getInstance(Mockito.any(Order.class));

        // exercise
        this.thread = new Thread(this.processor);
        this.thread.start();
        Thread.sleep(DEFAULT_SLEEP_TIME);

        // verify
        Order test = this.fulfilledOrderList.getNext();
        Assert.assertNotNull(test);
        Assert.assertEquals(order.getInstanceId(), test.getInstanceId());
        Assert.assertEquals(OrderState.FULFILLED, test.getOrderState());
        Assert.assertNull(this.spawningOrderList.getNext());
    }

    // test case: When running thread in the SpawningProcessor and the OrderType is not a
    // Compute, the processSpawningOrder() method must immediately change the OrderState to
    // Fulfilled by adding in that list, and removed from the Spawning list.
    @Test
    public void testRunProcessWhenOrderTypeIsAttachment() throws Exception {
        // set up
        Order order = new AttachmentOrder();
        order.setRequester(BaseUnitTests.LOCAL_MEMBER_ID);
        order.setProvider(BaseUnitTests.LOCAL_MEMBER_ID);
        order.setOrderStateInTestMode(OrderState.SPAWNING);
        this.spawningOrderList.addItem(order);
        Assert.assertNull(this.fulfilledOrderList.getNext());

        OrderInstance orderInstance = new ComputeInstance(FAKE_INSTANCE_ID);
        orderInstance.setReady();

        Mockito.doReturn(orderInstance).when(this.cloudConnector)
                .getInstance(Mockito.any(Order.class));

        // exercise
        this.thread = new Thread(this.processor);
        this.thread.start();
        Thread.sleep(DEFAULT_SLEEP_TIME);

        // verify
        Order test = this.fulfilledOrderList.getNext();
        Assert.assertNotNull(test);
        Assert.assertEquals(order.getInstanceId(), test.getInstanceId());
        Assert.assertEquals(OrderState.FULFILLED, test.getOrderState());
        Assert.assertNull(this.spawningOrderList.getNext());
    }

    // test case: When running thread in the SpawningProcessor and the InstanceState is not
    // Ready, the method processSpawningOrder() must not change OrderState to Fulfilled and must
    // remain in Spawning list.
    @Test
    public void testRunProcessComputeOrderWhenInstanceStateIsNotReady() throws Exception {
        // set up
        Order order = createLocalOrder(getLocalMemberId());
        order.setInstanceId(FAKE_INSTANCE_ID);
        order.setOrderStateInTestMode(OrderState.SPAWNING);
        this.spawningOrderList.addItem(order);

        OrderInstance orderInstance = new ComputeInstance(FAKE_INSTANCE_ID);
        orderInstance.setState(InstanceState.DISPATCHED);

        Mockito.doReturn(orderInstance).when(this.cloudConnector)
                .getInstance(Mockito.any(Order.class));

        // exercise
        this.thread = new Thread(this.processor);
        this.thread.start();

        // verify
        Assert.assertEquals(order, this.spawningOrderList.getNext());
        Assert.assertNull(this.fulfilledOrderList.getNext());
    }

    // test case: When running thread in the SpawningProcessor and the InstanceState is Ready, the
    // processSpawningOrder() method must change the OrderState to Fulfilled by adding in that list,
    // and removed from the Spawning list.
    @Test
    public void testRunProcessComputeOrderInstanceReachable() throws Exception {
        // set up
        Order order = createLocalOrder(getLocalMemberId());
        order.setInstanceId(FAKE_INSTANCE_ID);
        order.setOrderStateInTestMode(OrderState.SPAWNING);
        this.spawningOrderList.addItem(order);
        Assert.assertNull(this.fulfilledOrderList.getNext());

        OrderInstance orderInstance = new ComputeInstance(FAKE_INSTANCE_ID);
        orderInstance.setReady();

        Mockito.doReturn(orderInstance).when(this.cloudConnector)
                .getInstance(Mockito.any(Order.class));

        // exercise
        this.thread = new Thread(this.processor);
        this.thread.start();
        Thread.sleep(SPAWNING_SLEEP_TIME);

        // verify
        Order test = this.fulfilledOrderList.getNext();
        Assert.assertNotNull(test);
        Assert.assertEquals(order.getInstanceId(), test.getInstanceId());
        Assert.assertEquals(OrderState.FULFILLED, test.getOrderState());
        Assert.assertNull(this.spawningOrderList.getNext());
    }

    // test case: When running thread in the SpawningProcessor and the InstanceState is Failed,
    // the processSpawningOrder() method must change the OrderState to Failed by adding in that
    // list, and removed from the Spawning list.
    @Test
    public void testRunProcessComputeOrderWhenInstanceStateIsFailed() throws Exception {
        // set up
        Order order = createLocalOrder(getLocalMemberId());
        order.setInstanceId(FAKE_INSTANCE_ID);
        order.setOrderStateInTestMode(OrderState.SPAWNING);
        this.spawningOrderList.addItem(order);
        Assert.assertNull(this.failedOrderList.getNext());

        OrderInstance orderInstance = new ComputeInstance(FAKE_INSTANCE_ID);
        orderInstance.setHasFailed();

        Mockito.doReturn(orderInstance).when(this.cloudConnector)
                .getInstance(Mockito.any(Order.class));

        // exercise
        this.thread = new Thread(this.processor);
        this.thread.start();
        Thread.sleep(DEFAULT_SLEEP_TIME);

        // verify
        Order test = this.failedOrderList.getNext();
        Assert.assertNotNull(test);
        Assert.assertEquals(order.getInstanceId(), test.getInstanceId());
        Assert.assertEquals(OrderState.FAILED_AFTER_SUCCESSFUL_REQUEST, test.getOrderState());
        Assert.assertNull(this.spawningOrderList.getNext());
    }
    
    // test case: During a thread running in SpawningProcessor, if any
    // errors occur when attempting to get a cloud provider instance, the
    // processSpawningOrder method will catch an exception.
    @Test
    public void testRunProcessLocalOrderToCatchExceptionWhileTryingToGetInstance()
            throws InterruptedException, FogbowException {

        // set up
        Order order = createLocalOrder(getLocalMemberId());
        order.setInstanceId(FAKE_INSTANCE_ID);
        order.setOrderStateInTestMode(OrderState.SPAWNING);
        this.spawningOrderList.addItem(order);

        Mockito.doThrow(new RuntimeException()).when(this.cloudConnector).getInstance(Mockito.any(Order.class));

        // exercise
        this.thread = new Thread(this.processor);
        this.thread.start();
        Thread.sleep(DEFAULT_SLEEP_TIME);

        // verify
        Mockito.verify(this.processor, Mockito.times(1)).processSpawningOrder(order);
    }
    
    // test case: Check the throw of UnexpectedException when running the thread in
    // the SpawningProcessor, while running a local order.
    @Test
    public void testRunProcessLocalOrderThrowsUnexpectedException() throws InterruptedException, FogbowException {
        // set up
        Order order = createLocalOrder(getLocalMemberId());
        order.setInstanceId(FAKE_INSTANCE_ID);
        order.setOrderStateInTestMode(OrderState.SPAWNING);
        this.spawningOrderList.addItem(order);

        Mockito.doThrow(new UnexpectedException()).when(this.processor).processSpawningOrder(order);

        // exercise
        this.thread = new Thread(this.processor);
        this.thread.start();
        Thread.sleep(DEFAULT_SLEEP_TIME);

        // verify
        Mockito.verify(this.processor, Mockito.times(1)).processSpawningOrder(order);
    }
    
    // test case: When invoking the processSpawningOrder method and an error occurs
    // while trying to get an instance from the cloud provider, an
    // UnavailableProviderException will be throw.
    @Test(expected = UnavailableProviderException.class) // Verify
    public void testProcessSpawningOrderThrowsUnavailableProviderException() throws FogbowException {
        // set up
        Order order = createLocalOrder(getLocalMemberId());
        order.setInstanceId(FAKE_INSTANCE_ID);
        order.setOrderStateInTestMode(OrderState.SPAWNING);
        this.spawningOrderList.addItem(order);

        Mockito.doThrow(new UnavailableProviderException()).when(this.cloudConnector)
                .getInstance(Mockito.any(Order.class));

        // exercise
        this.processor.processSpawningOrder(order);
    }
    
    // test case: When calling the processSpawningOrder method and the
    // order instance is not found, it must change the order state to
    // FAILED_AFTER_SUCCESSFUL_REQUEST.
    @Test
    public void testProcessSpawningOrderWithInstanceNotFound() throws FogbowException {
        // set up
        Order order = createLocalOrder(getLocalMemberId());
        order.setInstanceId(FAKE_INSTANCE_ID);
        order.setOrderStateInTestMode(OrderState.SPAWNING);
        this.spawningOrderList.addItem(order);

        Mockito.doThrow(new InstanceNotFoundException()).when(this.cloudConnector)
                .getInstance(Mockito.any(Order.class));

        // exercise
        this.processor.processSpawningOrder(order);

        // verify
        Assert.assertEquals(OrderState.FAILED_AFTER_SUCCESSFUL_REQUEST, order.getOrderState());
    }
    
    // test case: When calling the processSpawningOrder method with a
    // remote member ID, the order state should not be changed.
    @Test
    public void testProcessSpawningOrderWithARemoteMember() throws FogbowException {
        // set up
        Order order = createLocalOrder(getLocalMemberId());
        order.setInstanceId(FAKE_INSTANCE_ID);
        order.setOrderStateInTestMode(OrderState.SPAWNING);
        this.spawningOrderList.addItem(order);

        this.processor = new SpawningProcessor(FAKE_REMOTE_MEMBER_ID,
                ConfigurationPropertyDefaults.FAILED_ORDERS_SLEEP_TIME);

        // exercise
        this.processor.processSpawningOrder(order);

        // verify
        Assert.assertEquals(OrderState.SPAWNING, order.getOrderState());
    }

}
