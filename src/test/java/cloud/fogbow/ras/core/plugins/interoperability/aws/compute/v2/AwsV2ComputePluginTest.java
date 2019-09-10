package cloud.fogbow.ras.core.plugins.interoperability.aws.compute.v2;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.verification.VerificationModeFactory;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;

import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.InstanceNotFoundException;
import cloud.fogbow.common.exceptions.NoAvailableResourcesException;
import cloud.fogbow.common.exceptions.UnexpectedException;
import cloud.fogbow.common.models.AwsV2User;
import cloud.fogbow.common.util.HomeDir;
import cloud.fogbow.ras.api.http.response.ComputeInstance;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.constants.SystemConstants;
import cloud.fogbow.ras.core.BaseUnitTests;
import cloud.fogbow.ras.core.TestUtils;
import cloud.fogbow.ras.core.datastore.DatabaseManager;
import cloud.fogbow.ras.core.models.orders.ComputeOrder;
import cloud.fogbow.ras.core.plugins.interoperability.aws.AwsV2ClientUtil;
import cloud.fogbow.ras.core.plugins.interoperability.aws.AwsV2CloudUtil;
import cloud.fogbow.ras.core.plugins.interoperability.aws.AwsV2StateMapper;
import cloud.fogbow.ras.core.plugins.interoperability.util.LaunchCommandGenerator;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.BlockDeviceMapping;
import software.amazon.awssdk.services.ec2.model.CpuOptions;
import software.amazon.awssdk.services.ec2.model.DescribeImagesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeImagesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.EbsBlockDevice;
import software.amazon.awssdk.services.ec2.model.EbsInstanceBlockDevice;
import software.amazon.awssdk.services.ec2.model.Image;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.InstanceBlockDeviceMapping;
import software.amazon.awssdk.services.ec2.model.InstanceNetworkInterface;
import software.amazon.awssdk.services.ec2.model.InstanceNetworkInterfaceAssociation;
import software.amazon.awssdk.services.ec2.model.InstanceNetworkInterfaceSpecification;
import software.amazon.awssdk.services.ec2.model.InstancePrivateIpAddress;
import software.amazon.awssdk.services.ec2.model.InstanceState;
import software.amazon.awssdk.services.ec2.model.InstanceType;
import software.amazon.awssdk.services.ec2.model.Reservation;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Volume;

@PrepareForTest({ AwsV2ClientUtil.class, AwsV2CloudUtil.class, DatabaseManager.class })
public class AwsV2ComputePluginTest extends BaseUnitTests {

    private static final String ANY_VALUE = "anything";
    private static final String AWS_TAG_NAME = "Name";
    private static final String CLOUD_NAME = "amazon";
    private static final String FAKE_DEFAULT_SECURITY_GROUP_ID = "fake-default-security-group-id";
    private static final String FAKE_IP_ADDRESS = "0.0.0.0";
    private static final String FAKE_SUBNET_ID = "fake-subnet-id";
    private static final String MEMORY_KEY = "memory";
    private static final String TEST_INSTANCE_TYPE = "t2.micro";
    private static final String VCPU_KEY = "vCPU";

    private static final int INSTANCE_TYPE_DEFAULT_RESOURCE_VALUE = 1;
    private static final int FLAVOR_CPU_VALUE = INSTANCE_TYPE_DEFAULT_RESOURCE_VALUE;
    private static final int FLAVOR_MEMORY_VALUE = INSTANCE_TYPE_DEFAULT_RESOURCE_VALUE;
    private static final int ZERO_VALUE = 0;
	
    private AwsV2ComputePlugin plugin;
    private Ec2Client client;
    private LaunchCommandGenerator launchCommandGenerator;

    @Before
    public void setUp() throws FogbowException {
        this.testUtils.mockReadOrdersFromDataBase();
        this.launchCommandGenerator = Mockito.mock(LaunchCommandGenerator.class);
        String awsConfFilePath = HomeDir.getPath() 
                + SystemConstants.CLOUDS_CONFIGURATION_DIRECTORY_NAME
                + File.separator 
                + CLOUD_NAME 
                + File.separator 
                + SystemConstants.CLOUD_SPECIFICITY_CONF_FILE_NAME;

        this.plugin = Mockito.spy(new AwsV2ComputePlugin(awsConfFilePath));
        this.plugin.setLaunchCommandGenerator(launchCommandGenerator);
        this.client = this.testUtils.getAwsMockedClient();
    }
	
    // test case: When calling the requestInstance method, with a compute order and
    // cloud user valid, a client is invoked to run instances, returning its ID.
    @Test
    public void testRequestInstance() throws FogbowException {
        // set up
        List networkOrderIds = createNetworkOrderIds();
        ComputeOrder order = this.testUtils.createLocalComputeOrder(networkOrderIds);
        AwsV2User cloudUser = Mockito.mock(AwsV2User.class);

        AwsHardwareRequirements flavor = createFlavor(null);
        Mockito.doReturn(flavor).when(this.plugin).findSmallestFlavor(Mockito.eq(order), Mockito.eq(cloudUser));

        RunInstancesRequest request = buildRunInstancesResquest(order, flavor);
        Mockito.doReturn(request).when(this.plugin).buildRequestInstance(Mockito.eq(order), Mockito.eq(flavor));

        Mockito.doReturn(TestUtils.FAKE_INSTANCE_ID).when(this.plugin).doRequestInstance(Mockito.eq(order),
                Mockito.eq(flavor), Mockito.eq(request), Mockito.eq(this.client));

        // exercise
        this.plugin.requestInstance(order, cloudUser);

        // verify
        PowerMockito.verifyStatic(AwsV2ClientUtil.class, VerificationModeFactory.times(TestUtils.RUN_ONCE));
        AwsV2ClientUtil.createEc2Client(Mockito.eq(cloudUser.getToken()), Mockito.anyString());

        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).findSmallestFlavor(Mockito.eq(order),
                Mockito.eq(cloudUser));
        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).buildRequestInstance(Mockito.eq(order),
                Mockito.eq(flavor));
        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).doRequestInstance(Mockito.eq(order),
                Mockito.eq(flavor), Mockito.eq(request), Mockito.eq(this.client));
    }

    // test case: check if the calls are made as expected when getInstance is
    // invoked properly
    @Test
    public void testGetInstance() throws FogbowException {
        // set up
        ComputeOrder order = this.testUtils.createLocalComputeOrder();
        AwsV2User cloudUser = Mockito.mock(AwsV2User.class);

        Mockito.doNothing().when(this.plugin).updateHardwareRequirements(Mockito.eq(cloudUser));

        ComputeInstance instance = Mockito.mock(ComputeInstance.class);
        Mockito.doReturn(instance).when(this.plugin).doGetInstance(Mockito.eq(order), Mockito.eq(this.client));

        // exercise
        this.plugin.getInstance(order, cloudUser);

        // verify
        PowerMockito.verifyStatic(AwsV2ClientUtil.class, VerificationModeFactory.times(TestUtils.RUN_ONCE));
        AwsV2ClientUtil.createEc2Client(Mockito.eq(cloudUser.getToken()), Mockito.anyString());

        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE))
                .updateHardwareRequirements(Mockito.eq(cloudUser));
        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).doGetInstance(Mockito.eq(order),
                Mockito.eq(this.client));
    }
	
    // test case: When calling the deleteInstance method, with a compute order and
    // cloud user valid, the instance in the cloud must be terminated.
    @Test
    public void testDeleteInstance() throws FogbowException {
        // set up
        ComputeOrder order = this.testUtils.createLocalComputeOrder();
        AwsV2User cloudUser = Mockito.mock(AwsV2User.class);
        
        Mockito.doNothing().when(this.plugin).doDeleteInstance(Mockito.eq(order.getInstanceId()), Mockito.eq(this.client));

        // exercise
        this.plugin.deleteInstance(order, cloudUser);

        // verify
        PowerMockito.verifyStatic(AwsV2ClientUtil.class, VerificationModeFactory.times(TestUtils.RUN_ONCE));
        AwsV2ClientUtil.createEc2Client(Mockito.eq(cloudUser.getToken()), Mockito.anyString());

        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE))
                .doDeleteInstance(Mockito.eq(order.getInstanceId()), Mockito.eq(this.client));
    }
	
	// test case: When calling the isReady method with the cloud state RUNNING,
	// this means that the state of compute is READY and it must return true.
	@Test
	public void testIsReadySuccessful() {
		// set up
		String cloudState = AwsV2StateMapper.RUNNING_STATE;

		// exercise
		boolean status = this.plugin.isReady(cloudState);

		// verify
		Assert.assertTrue(status);
	}

	// test case: When calling the isReady method with the cloud states different
	// than RUNNING, this means that the state of compute is not READY and it must
	// return false.
	@Test
	public void testIsReadyUnsuccessful() {
		// set up
		String[] cloudStates = { ANY_VALUE, AwsV2StateMapper.PENDING_STATE, AwsV2StateMapper.SHUTTING_DOWN_STATE,
				AwsV2StateMapper.STOPPING_STATE };

		for (String cloudState : cloudStates) {
			// exercise
			boolean status = this.plugin.isReady(cloudState);

			// verify
			Assert.assertFalse(status);
		}
	}
	
	// test case: Whenever you call the hasFailed method, no matter the value, it
	// must return false.
	@Test
	public void testHasFailed() {
		// set up
		String cloudState = ANY_VALUE;

		// exercise
		boolean status = this.plugin.hasFailed(cloudState);

		// verify
		Assert.assertFalse(status);
	}
	
	// test case: When calling the getMemoryValueFrom method, with a empty set
	// flavors, it must return a zero value.
	@Test
	public void testGetMemoryValueWithASetFlavorsEmpty() {
		// set up
		InstanceType instanceType = InstanceType.T1_MICRO;
		int expected = ZERO_VALUE;

		// exercise
		int memory = this.plugin.getMemoryValueFrom(instanceType);

		// verify
		Assert.assertTrue(this.plugin.getFlavors().isEmpty());
		Assert.assertEquals(expected, memory);
	}
	
    // test case: When calling the doDeleteInstance method, it must verify
    // that is call was successful.
    @Test
    public void testDoDeleteInstance() throws FogbowException {
        // set up
        String instanceId = TestUtils.FAKE_INSTANCE_ID;

        TerminateInstancesRequest request = TerminateInstancesRequest.builder()
                .instanceIds(instanceId)
                .build();

        Mockito.when(this.client.terminateInstances(Mockito.eq(request)))
                .thenReturn(TerminateInstancesResponse.builder().build());

        // exercise
        this.plugin.doDeleteInstance(instanceId, client);

        // verify
        Mockito.verify(this.client, Mockito.times(TestUtils.RUN_ONCE)).terminateInstances(Mockito.eq(request));
    }
    
    // test case: When calling the doDeleteInstance method, and an unexpected error
    // occurs, it must verify if an UnexpectedException has been thrown.
    @Test
    public void testDoDeleteInstanceFail() {
        // set up
        String instanceId = TestUtils.FAKE_INSTANCE_ID;

        Mockito.when(this.client.terminateInstances(Mockito.any(TerminateInstancesRequest.class)))
                .thenThrow(SdkClientException.builder().build());

        String expected = String.format(Messages.Error.ERROR_WHILE_REMOVING_RESOURCE, AwsV2ComputePlugin.RESOURCE_NAME,
                instanceId);

        try {
            // exercise
            this.plugin.doDeleteInstance(instanceId, this.client);
            Assert.fail();
        } catch (UnexpectedException e) {
            // verify
            Assert.assertEquals(expected, e.getMessage());
        }
    }
    
    // test case: When calling the doGetInstance method, it must verify
    // that is call was successful.
    @Test
    public void testDoGetInstance() throws FogbowException {
        // set up
        ComputeOrder order = this.testUtils.createLocalComputeOrder();

        DescribeInstancesResponse response = buildInstanceResponse();
        PowerMockito.mockStatic(AwsV2CloudUtil.class);
        Mockito.when(AwsV2CloudUtil.describeInstance(Mockito.eq(order.getInstanceId()), Mockito.eq(this.client)))
                .thenReturn(response);

        Instance instance = buildInstance();
        Mockito.when(AwsV2CloudUtil.getInstanceReservation(Mockito.eq(response))).thenReturn(instance);

        List volumes = createVolumesCollection();
        Mockito.when(AwsV2CloudUtil.getInstanceVolumes(Mockito.eq(instance), Mockito.eq(this.client)))
                .thenReturn(volumes);
        
        ComputeInstance computeInstance = Mockito.mock(ComputeInstance.class);
        Mockito.doReturn(computeInstance).when(this.plugin).buildComputeInstance(Mockito.eq(instance),
                Mockito.eq(volumes));

        // exercise
        this.plugin.doGetInstance(order, this.client);

        // verify
        PowerMockito.verifyStatic(AwsV2CloudUtil.class, VerificationModeFactory.times(TestUtils.RUN_ONCE));
        AwsV2CloudUtil.describeInstance(Mockito.eq(order.getInstanceId()), Mockito.eq(this.client));

        PowerMockito.verifyStatic(AwsV2CloudUtil.class, VerificationModeFactory.times(TestUtils.RUN_ONCE));
        AwsV2CloudUtil.getInstanceReservation(Mockito.eq(response));

        PowerMockito.verifyStatic(AwsV2CloudUtil.class, VerificationModeFactory.times(TestUtils.RUN_ONCE));
        AwsV2CloudUtil.getInstanceVolumes(Mockito.eq(instance), Mockito.eq(this.client));

        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).buildComputeInstance(Mockito.eq(instance),
                Mockito.eq(volumes));
    }
    
    // test case: When calling the buildComputeInstance method, it must verify
    // that is call was successful.
    @Test
    public void testBuildComputeInstance() {
        // set up
        Instance instance = buildInstance();
        Mockito.doReturn(FLAVOR_MEMORY_VALUE).when(this.plugin).getMemoryValueFrom(Mockito.eq(instance.instanceType()));
        
        List<Volume> volumes = createVolumesCollection();
        Mockito.doReturn(AwsV2ComputePlugin.ONE_GIGABYTE).when(this.plugin).getAllDisksSize(Mockito.eq(volumes));
        
        List<String> ipAddresses = buildIpAdressesCollection();
        Mockito.doReturn(ipAddresses).when(this.plugin).getIpAddresses(Mockito.eq(instance));

        // exercise
        this.plugin.buildComputeInstance(instance, volumes);

        // verify
        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).getMemoryValueFrom(Mockito.eq(instance.instanceType()));
        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).getAllDisksSize(Mockito.eq(volumes));
        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).getIpAddresses(Mockito.eq(instance));
    }
	
    // test case: When calling the getIpAddresses method, it must verify
    // that is call was successful.
    @Test
    public void testGetIpAddresses() {
        // set up
        Instance instance = buildInstance();

        List ipAddresses = buildIpAdressesCollection();
        Mockito.doReturn(ipAddresses).when(this.plugin).getPrivateIpAddresses(Mockito.eq(instance), Mockito.anyInt());
        Mockito.doReturn(FAKE_IP_ADDRESS).when(this.plugin).getPublicIpAddresses(Mockito.eq(instance),
                Mockito.anyInt());

        // exercise
        this.plugin.getIpAddresses(instance);

        // verify
        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).getPrivateIpAddresses(Mockito.eq(instance),
                Mockito.anyInt());
        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).getPublicIpAddresses(Mockito.eq(instance),
                Mockito.anyInt());
    }
    
    // test case: When calling the getPublicIpAddresses method, it must verify that
    // the obtained IP address is the same as expected.
    @Test
    public void testGetPublicIpAddresses() {
        // set up
        Instance instance = buildInstance();
        int index = ZERO_VALUE;

        String expected = FAKE_IP_ADDRESS;

        // exercise
        String ipAdress = this.plugin.getPublicIpAddresses(instance, index);

        // verify
        Assert.assertSame(expected, ipAdress);
    }
    
    // test case: When calling the getPrivateIpAddresses method, it must verify that
    // the obtained IPs addresses is the equals as expected.
    @Test
    public void testGetPrivateIpAddresses() {
        // set up
        Instance instance = buildInstance();
        int index = ZERO_VALUE;

        List expected = buildIpAdressesCollection();

        // exercise
        List ipAdresses = this.plugin.getPrivateIpAddresses(instance, index);

        // verify
        Assert.assertEquals(expected, ipAdresses);
    }
    
    // test case: When calling the getAllDisksSize method, it must verify that
    // the obtained size is the equals as expected.
    @Test
    public void testGetAllDisksSize() {
        // set up
        List volumes = createVolumesCollection();
        int expected = AwsV2ComputePlugin.ONE_GIGABYTE;
        
        // exercise
        int size = this.plugin.getAllDisksSize(volumes);
        
        // verify
        Assert.assertEquals(expected, size);
    }
    
    // test case: When calling the getMemoryValueFrom method, it must verify that
    // the obtained memory is the equals as expected.
    @Test
    public void testGetMemoryValueFrom() {
        // set up
        TreeSet flavors = createFlavorsCollection();
        Mockito.doReturn(flavors).when(this.plugin).getFlavors();

        int expected = FLAVOR_MEMORY_VALUE;

        // exercise
        int memory = this.plugin.getMemoryValueFrom(InstanceType.T2_MICRO);

        // verify
        Assert.assertEquals(expected, memory);
    }
    
    // test case: When calling the doRequestInstance method, it must verify
    // that is call was successful.
    @Test
    public void testDoRequestInstance() throws Exception {
        // set up
        ComputeOrder order = this.testUtils.createLocalComputeOrder();
        AwsHardwareRequirements flavor = createFlavor(null);
        Instance instance = mockRunningInstance();
        String tagName = AwsV2CloudUtil.AWS_TAG_NAME;

        PowerMockito.mockStatic(AwsV2CloudUtil.class);
        PowerMockito.doCallRealMethod().when(AwsV2CloudUtil.class, TestUtils.CREATE_TAGS_REQUEST_METHOD,
                Mockito.eq(instance.instanceId()), Mockito.eq(tagName), Mockito.eq(order.getName()),
                Mockito.eq(this.client));

        Mockito.doNothing().when(this.plugin).updateInstanceAllocation(Mockito.eq(order), Mockito.eq(flavor),
                Mockito.eq(instance), Mockito.eq(this.client));

        RunInstancesRequest request = buildRunInstancesResquest(order, flavor);
        
        // exercise
        this.plugin.doRequestInstance(order, flavor, request, this.client);

        // verify
        Mockito.verify(this.client, Mockito.times(TestUtils.RUN_ONCE)).runInstances(Mockito.eq(request));

        PowerMockito.verifyStatic(AwsV2CloudUtil.class);
        AwsV2CloudUtil.createTagsRequest(Mockito.eq(instance.instanceId()), Mockito.eq(tagName),
                Mockito.eq(order.getName()), Mockito.eq(this.client));

        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).updateInstanceAllocation(Mockito.eq(order),
                Mockito.eq(flavor), Mockito.eq(instance), Mockito.eq(this.client));
    }
    
    // test case: When calling the doRequestInstance method, and an unexpected error
    // occurs, it must verify if an UnexpectedException has been thrown.
    @Test
    public void testDoRequestInstanceFail() throws Exception {
        // set up
        ComputeOrder order = this.testUtils.createLocalComputeOrder();
        AwsHardwareRequirements flavor = createFlavor(null);
        RunInstancesRequest request = buildRunInstancesResquest(order, flavor);
        
        SdkClientException exception = SdkClientException.builder().build();
        Mockito.when(this.client.runInstances(request)).thenThrow(exception);
        
        String expected = String.format(Messages.Exception.GENERIC_EXCEPTION, exception);

        try {
            // exercise
            this.plugin.doRequestInstance(order, flavor, request, this.client);
            Assert.fail();
        } catch (UnexpectedException e) {
            // verify
            Assert.assertEquals(expected, e.getMessage());
        }
    }
    
    // test case: When calling the updateInstanceAllocation method, it must verify
    // that is call was successful.
    @Test
    public void testUpdateInstanceAllocation() throws FogbowException {
        // set up
        ComputeOrder order = this.testUtils.createLocalComputeOrder();
        AwsHardwareRequirements flavor = createFlavor(null);
        Instance instance = mockRunningInstance();

        String imageId = TestUtils.FAKE_IMAGE_ID;
        Image image = buildImage();
        
        Mockito.doReturn(image).when(this.plugin).getImageById(Mockito.eq(imageId), Mockito.eq(client));
        Mockito.doReturn(AwsV2ComputePlugin.ONE_GIGABYTE).when(this.plugin).getImageSize(Mockito.eq(image));

        // exercise
        this.plugin.updateInstanceAllocation(order, flavor, instance, client);

        // verify
        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).getImageById(Mockito.eq(imageId), Mockito.eq(this.client));
        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).getImageSize(Mockito.eq(image));
    }
    
    // test case: When calling the getImageById method, it must verify
    // that is call was successful.
    @Test
    public void testGetImageById() throws Exception {
        // set up
        String imageId = TestUtils.FAKE_IMAGE_ID;
        
        DescribeImagesRequest request = DescribeImagesRequest.builder()
                .imageIds(imageId)
                .build();
        
        DescribeImagesResponse response = buildDescribeImages();
        
        PowerMockito.mockStatic(AwsV2CloudUtil.class);
        PowerMockito.doReturn(response).when(AwsV2CloudUtil.class, TestUtils.DO_DESCRIBE_IMAGES_REQUEST_METHOD, Mockito.eq(request), Mockito.eq(this.client));
        
        Image expected = buildImage();

        // exercise
        Image image = this.plugin.getImageById(imageId, this.client);

        // verify
        PowerMockito.verifyStatic(AwsV2CloudUtil.class);
        AwsV2CloudUtil.doDescribeImagesRequest(Mockito.eq(request), Mockito.eq(this.client));
        
        Assert.assertEquals(expected, image);
    }
    
    // test case: When calling the getImageById method, with a null response or
    // empty image, it must verify if an InstanceNotFoundException has been thrown.
    @Test
    public void testGetImageByIdFail() throws Exception {
        // set up
        String imageId = TestUtils.FAKE_IMAGE_ID;

        DescribeImagesRequest request = DescribeImagesRequest.builder()
                .imageIds(imageId)
                .build();

        DescribeImagesResponse response = DescribeImagesResponse.builder().build();

        PowerMockito.mockStatic(AwsV2CloudUtil.class);
        PowerMockito.doReturn(response).when(AwsV2CloudUtil.class, TestUtils.DO_DESCRIBE_IMAGES_REQUEST_METHOD,
                Mockito.eq(request), Mockito.eq(this.client));

        String expected = Messages.Exception.IMAGE_NOT_FOUND;

        try {
            // exercise
            this.plugin.getImageById(imageId, this.client);
            Assert.fail();
        } catch (InstanceNotFoundException e) {
            // verify
            Assert.assertEquals(expected, e.getMessage());
        }
    }
    
    // test case: When calling the buildRequestInstance method, it must verify
    // that is call was successful.
    @Test
    public void testBuildRequestInstance() {
        // set up
        ComputeOrder order = this.testUtils.createLocalComputeOrder();
        AwsHardwareRequirements flavor = createFlavor(null);

        List networkOrderIds = createNetworkOrderIds();
        Mockito.doReturn(networkOrderIds).when(this.plugin).getSubnetIdsFrom(Mockito.eq(order));
        
        List networkInterfaces = createNetworkInterfaceCollection();
        Mockito.doReturn(networkInterfaces).when(this.plugin).loadNetworkInterfaces(Mockito.eq(networkOrderIds));
        
        RunInstancesRequest expected = buildRunInstancesResquest(order, flavor);

        // exercise
        RunInstancesRequest request = this.plugin.buildRequestInstance(order, flavor);

        // verify
        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).getSubnetIdsFrom(Mockito.eq(order));
        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE))
                .loadNetworkInterfaces(Mockito.eq(networkOrderIds));
        
        Assert.assertEquals(expected, request);
    }
    
    // test case: When calling the loadNetworkInterfaces method, it must verify
    // that is call was successful.
    @Test
    public void testLoadNetworkInterfaces() {
        // set up
        List subnetIds = createNetworkOrderIds();
        String subnetId = FAKE_SUBNET_ID;
        int index = ZERO_VALUE;

        InstanceNetworkInterfaceSpecification networkInterface = buildNetworkInterface();
        Mockito.doReturn(networkInterface).when(this.plugin).buildNetworkInterfaces(Mockito.eq(subnetId),
                Mockito.eq(index));

        // exercise
        this.plugin.loadNetworkInterfaces(subnetIds);

        // verify
        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).buildNetworkInterfaces(Mockito.eq(subnetId),
                Mockito.eq(index));
    }
    
    // test case: When calling the buildNetworkInterfaces method, it must verify
    // that the obtained network interface is the equals as expected.
    @Test
    public void testBuildNetworkInterfaces() {
        // set up
        String subnetId = FAKE_SUBNET_ID;
        int index = ZERO_VALUE;

        InstanceNetworkInterfaceSpecification expected = buildNetworkInterface();

        // exercise
        InstanceNetworkInterfaceSpecification networkInterface = this.plugin.buildNetworkInterfaces(subnetId, index);

        // verify
        Assert.assertEquals(expected, networkInterface);
    }
    
    // test case: When calling the getSubnetIdsFrom method, it must verify
    // that the obtained a list of sub-net ID is the equals as expected.
    @Test
    public void testGetSubnetIdsFromOrder() {
        // set up
        ComputeOrder order = this.testUtils.createLocalComputeOrder();
        
        List expected = createNetworkOrderIds();

        // exercise
        List subnetIds = this.plugin.getSubnetIdsFrom(order);

        // verify
        Assert.assertEquals(expected, subnetIds);
    }
    
    // test case: When calling the findSmallestFlavor method, it must verify
    // that is call was successful.
    @Test
    public void testFindSmallestFlavor() throws FogbowException {
        // set up
        ComputeOrder order = this.testUtils.createLocalComputeOrder();
        AwsV2User cloudUser = Mockito.mock(AwsV2User.class);

        Mockito.doNothing().when(this.plugin).updateHardwareRequirements(cloudUser);

        Map<String, String> requirements = new HashMap<String, String>();
        requirements.put(MEMORY_KEY, String.valueOf(TestUtils.MEMORY_VALUE));
        requirements.put(VCPU_KEY, String.valueOf(TestUtils.CPU_VALUE));

        TreeSet flavors = createFlavorsCollection();
        flavors.add(createFlavor(requirements));
        Mockito.doReturn(flavors).when(this.plugin).getFlavorsByRequirements(Mockito.any());

        // exercise
        this.plugin.findSmallestFlavor(order, cloudUser);

        // verify
        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE))
                .updateHardwareRequirements(Mockito.eq(cloudUser));
        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).getFlavorsByRequirements(Mockito.any());
    }
    
    // test case: When calling the findSmallestFlavor method, with a null response,
    // it must verify if an NoAvailableResourcesException has been thrown.
    @Test
    public void testFindSmallestFlavorFail() throws FogbowException {
        // set up
        ComputeOrder order = this.testUtils.createLocalComputeOrder();
        AwsV2User cloudUser = Mockito.mock(AwsV2User.class);

        Mockito.doNothing().when(this.plugin).updateHardwareRequirements(cloudUser);
        
        TreeSet flavors = createFlavorsCollection();
        Mockito.doReturn(flavors).when(this.plugin).getFlavorsByRequirements(Mockito.any());

        String expected = Messages.Exception.NO_MATCHING_FLAVOR;

        try {
            // exercise
            this.plugin.findSmallestFlavor(order, cloudUser);
            Assert.fail();
        } catch (NoAvailableResourcesException e) {
            // verify
            Assert.assertEquals(expected, e.getMessage());
        }
    }
    
//	// test case: When calling the getFlavorsByRequirements method, with a
//	// requirements map, it must filter the possibilities according to that map,
//	// returning the corresponding results.
//	@Test
//	public void testGetFlavorsByRequirementsSuccessful() throws FogbowException {
//
//		// set up
//		Ec2Client client = Mockito.mock(Ec2Client.class);
//		PowerMockito.mockStatic(AwsV2ClientUtil.class);
//		BDDMockito.given(AwsV2ClientUtil.createEc2Client(Mockito.anyString(), Mockito.anyString())).willReturn(client);
//		
//		DescribeImagesResponse response = createDescribeImage();
//		Mockito.when(client.describeImages(Mockito.any(DescribeImagesRequest.class))).thenReturn(response);
//
//		AwsV2User cloudUser = Mockito.mock(AwsV2User.class);
//		this.plugin.updateHardwareRequirements(cloudUser);
//
//		Map<String, String> requirements = new HashMap<String, String>();
//		requirements.put(AwsV2ComputePlugin.STORAGE_REQUIREMENT, "1x75-SSD-NVMe");
//		requirements.put(AwsV2ComputePlugin.BANDWIDTH_REQUIREMENT, "<3500");
//		requirements.put(AwsV2ComputePlugin.PERFORMANCE_REQUIREMENT, "<10");
//		requirements.put(AwsV2ComputePlugin.PROCESSOR_REQUIREMENT, "Intel_Xeon_Platinum_8175_3.1GHz");
//
//		ComputeOrder computeOrder = createComputeOrder(requirements);
//		int expected = AMOUNT_SSD_STORAGE;
//
//		// exercise
//		TreeSet<AwsHardwareRequirements> flavors = this.plugin.getFlavorsByRequirements(computeOrder.getRequirements());
//
//		// verify
//		PowerMockito.verifyStatic(AwsV2ClientUtil.class, VerificationModeFactory.times(1));
//		AwsV2ClientUtil.createEc2Client(Mockito.anyString(), Mockito.anyString());
//
//		Mockito.verify(this.plugin, Mockito.times(4)).filterFlavors(Mockito.any(), Mockito.any());
//		Assert.assertEquals(expected, flavors.size());
//	}
	
//	// test case: When calling the getFlavorsByRequirements method with a
//	// requirements map containing high-level graphical attributes, it must filter
//	// the possibilities according to that map and return a set with a
//	// higher-performing instance type.
//	@Test
//	public void testGetFlavorsByRequirementsWithHighPerformanceGraphic() throws FogbowException {
//
//		// set up
//		Ec2Client client = Mockito.mock(Ec2Client.class);
//		PowerMockito.mockStatic(AwsV2ClientUtil.class);
//		BDDMockito.given(AwsV2ClientUtil.createEc2Client(Mockito.anyString(), Mockito.anyString())).willReturn(client);
//		
//		DescribeImagesResponse response = createDescribeImage();
//		Mockito.when(client.describeImages(Mockito.any(DescribeImagesRequest.class))).thenReturn(response);
//
//		AwsV2User cloudUser = Mockito.mock(AwsV2User.class);
//		this.plugin.updateHardwareRequirements(cloudUser);
//
//		Map<String, String> requirements = new HashMap<String, String>();
//		requirements.put(AwsV2ComputePlugin.GRAPHIC_SHARING_REQUIREMENT, "NVLink");
//		requirements.put(AwsV2ComputePlugin.GRAPHIC_PROCESSOR_REQUIREMENT, "8");
//		requirements.put(AwsV2ComputePlugin.GRAPHIC_MEMORY_REQUIREMENT, "256");
//
//		ComputeOrder computeOrder = createComputeOrder(requirements);
//		String expected = InstanceType.P3_DN_24_XLARGE.toString();
//
//		// exercise
//		TreeSet<AwsHardwareRequirements> flavors = this.plugin.getFlavorsByRequirements(computeOrder.getRequirements());
//
//		// verify
//		PowerMockito.verifyStatic(AwsV2ClientUtil.class, VerificationModeFactory.times(1));
//		AwsV2ClientUtil.createEc2Client(Mockito.anyString(), Mockito.anyString());
//
//		Mockito.verify(this.plugin, Mockito.times(3)).filterFlavors(Mockito.any(), Mockito.any());
//		Assert.assertEquals(expected, flavors.first().getName());
//	}
	
//	// test case: When calling the getFlavorsByRequirements method with a
//	// requirements map containing the most demanding graphics emulation attributes,
//	// it must filter the possibilities according to that map and return a set with
//	// an instance type with the highest performance of this level.
//	@Test
//	public void testGetFlavorsByRequirementsWithGraphicEmulationAtribute() throws FogbowException {
//
//		// set up
//		Ec2Client client = Mockito.mock(Ec2Client.class);
//		PowerMockito.mockStatic(AwsV2ClientUtil.class);
//		BDDMockito.given(AwsV2ClientUtil.createEc2Client(Mockito.anyString(), Mockito.anyString())).willReturn(client);
//		
//		DescribeImagesResponse response = createDescribeImage();
//		Mockito.when(client.describeImages(Mockito.any(DescribeImagesRequest.class))).thenReturn(response);
//
//		AwsV2User cloudUser = Mockito.mock(AwsV2User.class);
//		this.plugin.updateHardwareRequirements(cloudUser);
//
//		Map<String, String> requirements = new HashMap<String, String>();
//		requirements.put(AwsV2ComputePlugin.PROCESSOR_REQUIREMENT, "Intel_Xeon_E5-2686_v4_2.3GHz");
//		requirements.put(AwsV2ComputePlugin.GRAPHIC_EMULATION_REQUIREMENT, "8");
//
//		ComputeOrder computeOrder = createComputeOrder(requirements);
//		String expected = InstanceType.F1_16_XLARGE.toString();
//
//		// exercise
//		TreeSet<AwsHardwareRequirements> flavors = this.plugin.getFlavorsByRequirements(computeOrder.getRequirements());
//
//		// verify
//		PowerMockito.verifyStatic(AwsV2ClientUtil.class, VerificationModeFactory.times(1));
//		AwsV2ClientUtil.createEc2Client(Mockito.anyString(), Mockito.anyString());
//
//		Mockito.verify(this.plugin, Mockito.times(2)).filterFlavors(Mockito.any(), Mockito.any());
//		Assert.assertEquals(expected, flavors.first().getName());
//	}
	
//	// test case: When calling the getFlavorsByRequirements method, with a null map,
//	// there will be no filter to limit the results, returning all the flavors
//	// obtained in the last update.
//	@Test
//	public void testGetFlavorsByRequirementsWithNullMap() throws FogbowException {
//		// set up
//		Ec2Client client = Mockito.mock(Ec2Client.class);
//		PowerMockito.mockStatic(AwsV2ClientUtil.class);
//		BDDMockito.given(AwsV2ClientUtil.createEc2Client(Mockito.anyString(), Mockito.anyString())).willReturn(client);
//		
//		DescribeImagesResponse response = createDescribeImage();
//		Mockito.when(client.describeImages(Mockito.any(DescribeImagesRequest.class))).thenReturn(response);
//
//		AwsV2User cloudUser = Mockito.mock(AwsV2User.class);
//		this.plugin.updateHardwareRequirements(cloudUser);
//
//		Map<String, String> requirements = null;
//		ComputeOrder computeOrder = createComputeOrder(requirements);
//		int expected = this.plugin.loadLinesFromFlavorFile().size() - 1;
//
//		// exercise
//		TreeSet<AwsHardwareRequirements> flavors = this.plugin.getFlavorsByRequirements(computeOrder.getRequirements());
//
//		// verify
//		PowerMockito.verifyStatic(AwsV2ClientUtil.class, VerificationModeFactory.times(1));
//		AwsV2ClientUtil.createEc2Client(Mockito.anyString(), Mockito.anyString());
//
//		Assert.assertEquals(expected, flavors.size());
//	}
	
//	// test case: When calling the getFlavorsByRequirements method, with an empty
//	// map, there will be no filter to limit the results, returning all the flavors
//	// obtained in the last update.
//	@Test
//	public void testGetFlavorsByRequirementsWithEmptyMap() throws FogbowException {
//		// set up
//		Ec2Client client = Mockito.mock(Ec2Client.class);
//		PowerMockito.mockStatic(AwsV2ClientUtil.class);
//		BDDMockito.given(AwsV2ClientUtil.createEc2Client(Mockito.anyString(), Mockito.anyString())).willReturn(client);
//		
//		DescribeImagesResponse response = createDescribeImage();
//		Mockito.when(client.describeImages(Mockito.any(DescribeImagesRequest.class))).thenReturn(response);
//
//		AwsV2User cloudUser = Mockito.mock(AwsV2User.class);
//		this.plugin.updateHardwareRequirements(cloudUser);
//
//		Map<String, String> requirements = new HashMap<String, String>();
//		ComputeOrder computeOrder = createComputeOrder(requirements);
//		int expected = this.plugin.loadLinesFromFlavorFile().size() - 1;
//
//		// exercise
//		TreeSet<AwsHardwareRequirements> flavors = this.plugin.getFlavorsByRequirements(computeOrder.getRequirements());
//
//		// verify
//		PowerMockito.verifyStatic(AwsV2ClientUtil.class, VerificationModeFactory.times(1));
//		AwsV2ClientUtil.createEc2Client(Mockito.anyString(), Mockito.anyString());
//
//		Assert.assertEquals(expected, flavors.size());
//	}
		
//	// test case: When calling the loadLinesFromFlavorFile method, without a valid
//	// file path, the ConfigurationErrorException will be thrown.
//	@Test(expected = ConfigurationErrorException.class) // verify
//	public void testLoadLinesFromFlavorFileUnsuccessful() throws ConfigurationErrorException {
//		// set up
//		Mockito.doReturn(ANY_VALUE).when(this.plugin).getFlavorsFilePath();
//
//		// exercise
//		this.plugin.loadLinesFromFlavorFile();
//	}
		
//	// test case: When calling the updateHardwareRequirements method, with a valid
//	// cloud user, there will be updating the set of available flavors.
//	@Test
//	public void testUpdateHardwareRequirementsSuccessful() throws FogbowException {
//		// set up
//		Ec2Client client = Mockito.mock(Ec2Client.class);
//		PowerMockito.mockStatic(AwsV2ClientUtil.class);
//		BDDMockito.given(AwsV2ClientUtil.createEc2Client(Mockito.anyString(), Mockito.anyString())).willReturn(client);
//		
//		DescribeImagesResponse response = createDescribeImage();
//		Mockito.when(client.describeImages(Mockito.any(DescribeImagesRequest.class))).thenReturn(response);
//
//		AwsV2User cloudUser = Mockito.mock(AwsV2User.class);
//
//		int expected = this.plugin.loadLinesFromFlavorFile().size() - 1;
//
//		// exercise
//		this.plugin.updateHardwareRequirements(cloudUser);
//
//		// verify
//		Assert.assertEquals(expected, this.plugin.getFlavors().size());
//	}

//	// test case: When calling the getImageById method, and no image is returned ,
//	// an InstanceNotFoundException will be thrown.
//	@Test(expected = InstanceNotFoundException.class) // verify
//	public void testGetImageByIdUnsuccessful() throws FogbowException {
//		// set up
//		Ec2Client client = Mockito.mock(Ec2Client.class);
//		PowerMockito.mockStatic(AwsV2ClientUtil.class);
//		BDDMockito.given(AwsV2ClientUtil.createEc2Client(Mockito.anyString(), Mockito.anyString())).willReturn(client);
//
//		DescribeImagesResponse response = null;
//		Mockito.when(client.describeImages(Mockito.any(DescribeImagesRequest.class))).thenReturn(response);
//
//		String imageId = TestUtils.FAKE_IMAGE_ID;
//
//		// exercise
//		this.plugin.getImageById(imageId, client);
//	}
	
	private DescribeImagesResponse buildDescribeImages() {
        DescribeImagesResponse response = DescribeImagesResponse.builder()
                .images(buildImage())
                .build();
        
        return response;
    }
	
	private Image buildImage() {
        EbsBlockDevice ebsBlockDevice = EbsBlockDevice.builder()
                .volumeSize(AwsV2ComputePlugin.ONE_GIGABYTE)
                .build();
        
        BlockDeviceMapping blockDeviceMapping = BlockDeviceMapping.builder()
                .ebs(ebsBlockDevice)
                .build();
        
        Image image = Image.builder()
                .imageId(TestUtils.FAKE_IMAGE_ID)
                .blockDeviceMappings(blockDeviceMapping)
                .build();
        
        return image;
    }
	
	private Instance mockRunningInstance() {
        Instance instance = buildInstance();

        RunInstancesResponse response = RunInstancesResponse.builder()
                .instances(instance)
                .build();

        Mockito.when(this.client.runInstances(Mockito.any(RunInstancesRequest.class))).thenReturn(response);
        
        return instance;
    }
	
    private TreeSet<AwsHardwareRequirements> createFlavorsCollection() {
        AwsHardwareRequirements[] flavors = { createFlavor(null) };
        List flavorsList = Arrays.asList(flavors);
        return new TreeSet(flavorsList);
    }
	
	private List<String> buildIpAdressesCollection() {
        String[] ipAdresses = { FAKE_IP_ADDRESS }; 
        return Arrays.asList(ipAdresses);
    }
	
    private List<Volume> createVolumesCollection() {
        Volume[] volumes = { 
                Volume.builder()
                    .volumeId(TestUtils.FAKE_VOLUME_ID)
                    .size(AwsV2ComputePlugin.ONE_GIGABYTE)
                    .build() 
                };
        return Arrays.asList(volumes);
    }
	
    private Instance buildInstance() {
        EbsInstanceBlockDevice ebs = EbsInstanceBlockDevice.builder()
                .volumeId(TestUtils.FAKE_VOLUME_ID)
                .build();
        
        InstanceBlockDeviceMapping blockDeviceMapping = InstanceBlockDeviceMapping.builder()
                .ebs(ebs)
                .build();
        
        CpuOptions cpuOptions = CpuOptions.builder()
                .coreCount(FLAVOR_CPU_VALUE)
                .build();
        
        InstancePrivateIpAddress instancePrivateIpAddress = InstancePrivateIpAddress.builder()
                .privateIpAddress(FAKE_IP_ADDRESS)
                .build();
        
        InstanceNetworkInterfaceAssociation association = InstanceNetworkInterfaceAssociation.builder()
                .publicIp(FAKE_IP_ADDRESS)
                .build();
        
        InstanceNetworkInterface instanceNetworkInterface = InstanceNetworkInterface.builder()
                .privateIpAddresses(instancePrivateIpAddress)
                .association(association)
                .build();
        
        InstanceState instanceState = InstanceState.builder()
                .name(AwsV2StateMapper.AVAILABLE_STATE)
                .build();
        
        Tag tag = Tag.builder()
                .key(AWS_TAG_NAME)
                .value(TestUtils.FAKE_INSTANCE_NAME)
                .build();
        
        Instance instance = Instance.builder()
                .blockDeviceMappings(blockDeviceMapping)
                .cpuOptions(cpuOptions)
                .imageId(TestUtils.FAKE_INSTANCE_ID)
                .instanceId(TestUtils.FAKE_INSTANCE_ID)
                .instanceType(InstanceType.T1_MICRO)
                .networkInterfaces(instanceNetworkInterface)
                .state(instanceState)
                .tags(tag)
                .build();
        
        return instance;
    }
    
    private DescribeInstancesResponse buildInstanceResponse() {
        Instance instance = buildInstance();
        
        Reservation reservation = Reservation.builder()
                .instances(instance)
                .build();
        
        DescribeInstancesResponse response = DescribeInstancesResponse.builder()
                .reservations(reservation)
                .build();
        
        return response;
    }
    
    private RunInstancesRequest buildRunInstancesResquest(ComputeOrder order, AwsHardwareRequirements flavor) {
        RunInstancesRequest request = RunInstancesRequest.builder()
                .imageId(flavor.getImageId())
                .instanceType(InstanceType.T2_MICRO)
                .maxCount(AwsV2ComputePlugin.INSTANCES_LAUNCH_NUMBER)
                .minCount(AwsV2ComputePlugin.INSTANCES_LAUNCH_NUMBER)
                .networkInterfaces(createNetworkInterfaceCollection())
                .userData(this.launchCommandGenerator.createLaunchCommand(order))
                .build();
        
        return request;
    }

    private List<InstanceNetworkInterfaceSpecification> createNetworkInterfaceCollection() {
        InstanceNetworkInterfaceSpecification[] networkInterfaces = { 
                buildNetworkInterface() 
                };
        return Arrays.asList(networkInterfaces);
    }

    private InstanceNetworkInterfaceSpecification buildNetworkInterface() {
        InstanceNetworkInterfaceSpecification networkInterface = InstanceNetworkInterfaceSpecification.builder()
                .subnetId(FAKE_SUBNET_ID)
                .deviceIndex(ZERO_VALUE)
                .groups(FAKE_DEFAULT_SECURITY_GROUP_ID)
                .build();
        
        return networkInterface;
    }
    
    private AwsHardwareRequirements createFlavor(Map<String, String> requirements) {
        String name = TEST_INSTANCE_TYPE;
        String flavorId = TestUtils.FAKE_INSTANCE_ID;
        int cpu = selectValueBy(requirements, VCPU_KEY);
        int memory = selectValueBy(requirements, MEMORY_KEY);
        int disk = TestUtils.DISK_VALUE;
        String imageId = TestUtils.FAKE_IMAGE_ID;
        return new AwsHardwareRequirements(name, flavorId, cpu, memory, disk, imageId, requirements);
    }

    private int selectValueBy(Map<String, String> requirements, String key) {
        if (requirements != null && requirements.containsKey(key)) {
            return Integer.parseInt(requirements.get(key));
        }
        return INSTANCE_TYPE_DEFAULT_RESOURCE_VALUE;
    }
	
    private List<String> createNetworkOrderIds() {
        String[] networkOrderIds = { FAKE_SUBNET_ID };
        return Arrays.asList(networkOrderIds);
    }
    
}
