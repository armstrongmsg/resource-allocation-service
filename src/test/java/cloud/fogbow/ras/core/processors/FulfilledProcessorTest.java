package cloud.fogbow.ras.core.processors;

import cloud.fogbow.common.constants.FogbowConstants;
import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.UnexpectedException;
import cloud.fogbow.common.models.FederationUser;
import cloud.fogbow.ras.constants.ConfigurationPropertyDefaults;
import cloud.fogbow.ras.constants.ConfigurationPropertyKeys;
import cloud.fogbow.ras.core.BaseUnitTests;
import cloud.fogbow.ras.core.PropertiesHolder;
import cloud.fogbow.ras.core.SharedOrderHolders;
import cloud.fogbow.ras.core.cloudconnector.CloudConnectorFactory;
import cloud.fogbow.ras.core.cloudconnector.LocalCloudConnector;
import cloud.fogbow.ras.core.models.UserData;
import cloud.fogbow.ras.core.models.instances.ComputeInstance;
import cloud.fogbow.ras.core.models.instances.Instance;
import cloud.fogbow.ras.core.models.instances.InstanceState;
import cloud.fogbow.ras.core.models.linkedlists.ChainedList;
import cloud.fogbow.ras.core.models.orders.ComputeOrder;
import cloud.fogbow.ras.core.models.orders.Order;
import cloud.fogbow.ras.core.models.orders.OrderState;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@RunWith(PowerMockRunner.class)
@PrepareForTest(CloudConnectorFactory.class)
public class FulfilledProcessorTest extends BaseUnitTests {

    private static final String REMOTE_MEMBER_ID = "fake-intercomponent-member";
    private static final String FAKE_INSTANCE_ID = "fake-instance-id";
    private static final String FAKE_INSTANCE_NAME = "fake-instance-name";
    private static final String FAKE_IMAGE_NAME = "fake-image-name";
    private static final String FAKE_PUBLIC_KEY = "fake-public-key";
    private static final String FAKE_SOURCE = "fake-source";
    private static final String FAKE_TARGET = "fake-target";
    private static final String FAKE_DEVICE = "fake-device";

    /**
     * Maximum value that the thread should wait in sleep time
     */
    private static final int MAX_SLEEP_TIME = 10000;
    private static final int DEFAULT_SLEEP_TIME = 500;

    private ChainedList failedOrderList;
    private ChainedList fulfilledOrderList;
    private FulfilledProcessor fulfilledProcessor;
    private LocalCloudConnector localCloudConnector;
    private Properties properties;
    private Thread thread;

    @Before
    public void setUp() throws UnexpectedException {

        super.mockReadOrdersFromDataBase();

        PropertiesHolder propertiesHolder = PropertiesHolder.getInstance();

        this.properties = propertiesHolder.getProperties();
        this.properties.put(ConfigurationPropertyKeys.XMPP_JID_KEY, BaseUnitTests.LOCAL_MEMBER_ID);

        this.localCloudConnector = Mockito.mock(LocalCloudConnector.class);

        SharedOrderHolders sharedOrderHolders = SharedOrderHolders.getInstance();
        this.fulfilledOrderList = sharedOrderHolders.getFulfilledOrdersList();
        this.failedOrderList = sharedOrderHolders.getFailedAfterSuccessfulRequestOrdersList();

        this.thread = null;
    }

    @After
    public void tearDown() {
        if (this.thread != null) {
            this.thread.interrupt();
        }
        super.tearDown();
    }

    // test case: When running thread in FulfilledProcessor, if the instance state is Ready,
    // the method processFulfilledOrder() must not change OrderState to Failed and the order
    // must remain in the Fulfilled list.
    @Test
    public void testRunProcessLocalComputeOrderWithInstanceReady()
            throws FogbowException, InterruptedException {

        // set up
        Order order = this.createOrder();
        order.setOrderStateInTestMode(OrderState.FULFILLED);
        this.fulfilledOrderList.addItem(order);
        Assert.assertNull(this.failedOrderList.getNext());

        Instance orderInstance = new ComputeInstance(FAKE_INSTANCE_ID);
        orderInstance.setState(InstanceState.READY);
        order.setInstanceId(FAKE_INSTANCE_ID);

        mockCloudConnectorFactory(orderInstance);

        // exercise
        this.thread = new Thread(this.fulfilledProcessor);
        this.thread.start();
        Thread.sleep(DEFAULT_SLEEP_TIME);

        // verify
        Assert.assertNotNull(this.fulfilledOrderList.getNext());
        Assert.assertNull(this.failedOrderList.getNext());
    }

    // test case: When running thread in the FulfilledProcessor and the InstanceState is Failed,
    // the processFulfilledOrder() method must change the OrderState to Failed by adding in that
    // list, and removed from the Fulfilled list.
    @Test
    public void testRunProcessLocalComputeOrderWhenInstanceStateIsFailed()
            throws FogbowException, InterruptedException {

        // set up
        Order order = this.createOrder();
        order.setOrderStateInTestMode(OrderState.FULFILLED);

        Instance orderInstance = new ComputeInstance(FAKE_INSTANCE_ID);
        orderInstance.setState(InstanceState.FAILED);
        order.setInstanceId(FAKE_INSTANCE_ID);

        mockCloudConnectorFactory(orderInstance);

        this.fulfilledOrderList.addItem(order);
        Assert.assertNull(this.failedOrderList.getNext());

        // exercise
        spyFulfiledProcessor();

        this.thread = new Thread(this.fulfilledProcessor);
        this.thread.start();
        Thread.sleep(DEFAULT_SLEEP_TIME);

        // verify
        Order test = this.failedOrderList.getNext();
        Assert.assertNotNull(test);
        Assert.assertEquals(order.getInstanceId(), test.getInstanceId());
        Assert.assertEquals(OrderState.FAILED_AFTER_SUCCESSUL_REQUEST, test.getOrderState());
        Assert.assertNull(this.fulfilledOrderList.getNext());
    }

    // test case: In calling the processFulfilledOrder() method for any order other than Fulfilled,
    // you must not make state transition by keeping the order in your source list.
    @Test
    public void testProcessComputeOrderNotFulfilled()
            throws FogbowException, InterruptedException {

        // set up
        Order order = this.createOrder();
        order.setOrderStateInTestMode(OrderState.FAILED_AFTER_SUCCESSUL_REQUEST);
        this.failedOrderList.addItem(order);
        Assert.assertNull(this.fulfilledOrderList.getNext());

        // exercise
        spyFulfiledProcessor();
        this.fulfilledProcessor.processFulfilledOrder(order);

        // verify
        Assert.assertEquals(order, this.failedOrderList.getNext());
        Assert.assertNull(this.fulfilledOrderList.getNext());
    }

    // test case: When running thread in the FulfilledProcessor without a LocalMember, the method
    // processFulfilledOrder() must not change OrderState to Failed and must remain in Fulfilled
    // list.
    @Test
    public void testRunProcessLocalComputeOrderWithoutLocalMember()
            throws FogbowException, InterruptedException {

        // set up
        Order order = this.createOrder();
        order.setOrderStateInTestMode(OrderState.FULFILLED);
        this.fulfilledOrderList.addItem(order);
        Assert.assertNull(this.failedOrderList.getNext());

        this.fulfilledProcessor = new FulfilledProcessor(REMOTE_MEMBER_ID,
                ConfigurationPropertyDefaults.FULFILLED_ORDERS_SLEEP_TIME);

        // exercise
        this.thread = new Thread(this.fulfilledProcessor);
        this.thread.start();
        Thread.sleep(DEFAULT_SLEEP_TIME);

        // verify
        Assert.assertEquals(order, this.fulfilledOrderList.getNext());
        Assert.assertNull(this.failedOrderList.getNext());
    }

    // test case: When running thread in the FulfilledProcessor and the InstanceState is Ready, the
    // method processFulfilledOrder() must not change OrderState to Failed and must remain in
    // Fulfilled list.
    @Test
    public void testRunProcessLocalComputeOrderInstanceReachable() throws Exception {

        // set up
        Order order = this.createOrder();
        order.setOrderStateInTestMode(OrderState.FULFILLED);
        this.fulfilledOrderList.addItem(order);
        Assert.assertNull(this.failedOrderList.getNext());

        Instance orderInstance = new ComputeInstance(FAKE_INSTANCE_ID);
        orderInstance.setState(InstanceState.READY);
        order.setInstanceId(FAKE_INSTANCE_ID);

        // exercise
        mockCloudConnectorFactory(orderInstance);
        this.thread = new Thread(this.fulfilledProcessor);
        this.thread.start();
        Thread.sleep(DEFAULT_SLEEP_TIME);

        // verify
        Assert.assertNotNull(this.fulfilledOrderList.getNext());
        Assert.assertNull(this.failedOrderList.getNext());
    }

    // test case: When running thread in the FulfilledProcessor and the InstanceState is not Ready,
    // the processFulfilledOrder() method must change the OrderState to Failed by adding in that
    // list, and removed from the Fulfilled list.
    @Test
    public void testRunProcessLocalComputeOrderInstanceNotReachable() throws Exception {

        // set up
        Order order = this.createOrder();
        order.setOrderStateInTestMode(OrderState.FULFILLED);
        this.fulfilledOrderList.addItem(order);
        Assert.assertNull(this.failedOrderList.getNext());

        Instance orderInstance = new ComputeInstance(FAKE_INSTANCE_ID);
        orderInstance.setState(InstanceState.FAILED);
        order.setInstanceId(FAKE_INSTANCE_ID);

        mockCloudConnectorFactory(orderInstance);

        // exercise
        this.thread = new Thread(this.fulfilledProcessor);
        this.thread.start();

        /**
         * here may be a false positive depending on how long the machine will take to run the test
         */
        Thread.sleep(MAX_SLEEP_TIME);

        // verify
        Order test = this.failedOrderList.getNext();
        Assert.assertNotNull(test);
        Assert.assertEquals(order.getInstanceId(), test.getInstanceId());
        Assert.assertEquals(OrderState.FAILED_AFTER_SUCCESSUL_REQUEST, test.getOrderState());
        Assert.assertNull(this.fulfilledOrderList.getNext());
    }

    // test case: When running thread in the FulfilledProcessor and the InstanceState is Failed,
    // the processFulfilledOrder() method must change the OrderState to Failed by adding in that
    // list, and removed from the Fulfilled list.
    @Test
    public void testRunProcessLocalComputeOrderInstanceFailed() throws Exception {

        // set up
        Order order = this.createOrder();
        order.setOrderStateInTestMode(OrderState.FULFILLED);
        this.fulfilledOrderList.addItem(order);
        Assert.assertNull(this.failedOrderList.getNext());

        Instance orderInstance = new ComputeInstance(FAKE_INSTANCE_ID);
        orderInstance.setState(InstanceState.FAILED);
        order.setInstanceId(FAKE_INSTANCE_ID);

        mockCloudConnectorFactory(orderInstance);

        // exercise
        this.thread = new Thread(this.fulfilledProcessor);
        this.thread.start();
        Thread.sleep(DEFAULT_SLEEP_TIME);

        // verify
        Order test = this.failedOrderList.getNext();
        Assert.assertNotNull(test);
        Assert.assertEquals(order.getInstanceId(), test.getInstanceId());
        Assert.assertEquals(OrderState.FAILED_AFTER_SUCCESSUL_REQUEST, test.getOrderState());
        Assert.assertNull(this.fulfilledOrderList.getNext());
    }

    // test case: When running thread in the FulfilledProcessor with OrderState Null must throw a
    // ThrowableException.
    @Test
    public void testRunThrowableExceptionWhileTryingToProcessOrderStateNull()
            throws InterruptedException, UnexpectedException {

        // set up
        Order order = Mockito.mock(Order.class);
        OrderState state = null;
        order.setOrderStateInTestMode(state);
        this.fulfilledOrderList.addItem(order);

        spyFulfiledProcessor();

        // exercise
        Mockito.doThrow(new RuntimeException()).when(this.fulfilledProcessor)
                .processFulfilledOrder(order);

        this.thread = new Thread(this.fulfilledProcessor);
        this.thread.start();
        Thread.sleep(DEFAULT_SLEEP_TIME);

        // verify
        Mockito.verify(this.fulfilledProcessor, Mockito.times(1)).processFulfilledOrder(order);
    }

    // test case: When running thread in the FulfilledProcessor with OrderState Null must throw a
    // UnexpectedException.
    @Test
    public void testThrowUnexpectedExceptionWhileTryingToProcessOrder()
            throws InterruptedException, UnexpectedException {

        // set up
        Order order = Mockito.mock(Order.class);
        OrderState state = null;
        order.setOrderStateInTestMode(state);
        this.fulfilledOrderList.addItem(order);

        spyFulfiledProcessor();

        Mockito.doThrow(new UnexpectedException()).when(this.fulfilledProcessor)
                .processFulfilledOrder(order);

        // exercise
        this.thread = new Thread(this.fulfilledProcessor);
        this.thread.start();
        Thread.sleep(DEFAULT_SLEEP_TIME);

        // verify
        Mockito.verify(this.fulfilledProcessor, Mockito.times(1)).processFulfilledOrder(order);
    }

    private Order createOrder() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(FogbowConstants.PROVIDER_ID_KEY, "fake-token-provider");
        attributes.put(FogbowConstants.USER_ID_KEY, "fake-id");
        attributes.put(FogbowConstants.USER_NAME_KEY, "fake-user");
        attributes.put(FogbowConstants.TOKEN_VALUE_KEY, "token-value");
        FederationUser federationUser = new FederationUser(attributes);

        UserData userData = Mockito.mock(UserData.class);

        String requestingMember =
                String.valueOf(this.properties.get(ConfigurationPropertyKeys.XMPP_JID_KEY));

        String providingMember =
                String.valueOf(this.properties.get(ConfigurationPropertyKeys.XMPP_JID_KEY));

        Order order = new ComputeOrder(federationUser, requestingMember, providingMember, "default", FAKE_INSTANCE_NAME, 8, 1024,
                30, FAKE_IMAGE_NAME, mockUserData(), FAKE_PUBLIC_KEY, null);

        return order;
    }

    private void mockCloudConnectorFactory(Instance orderInstance)
            throws FogbowException {

        CloudConnectorFactory cloudConnectorFactory = Mockito.mock(CloudConnectorFactory.class);
        Mockito.when(cloudConnectorFactory.getCloudConnector(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(localCloudConnector);

        Mockito.doReturn(orderInstance).when(this.localCloudConnector)
                .getInstance(Mockito.any(Order.class));

        PowerMockito.mockStatic(CloudConnectorFactory.class);
        BDDMockito.given(CloudConnectorFactory.getInstance()).willReturn(cloudConnectorFactory);

        spyFulfiledProcessor();
    }

    private void spyFulfiledProcessor() {
        this.fulfilledProcessor = Mockito.spy(new FulfilledProcessor(BaseUnitTests.LOCAL_MEMBER_ID,
                //this.tunnelingService, this.sshConnectivity,
                ConfigurationPropertyDefaults.FULFILLED_ORDERS_SLEEP_TIME));
    }

}