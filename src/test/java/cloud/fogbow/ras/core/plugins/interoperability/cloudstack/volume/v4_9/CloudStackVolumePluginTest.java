package cloud.fogbow.ras.core.plugins.interoperability.cloudstack.volume.v4_9;

import cloud.fogbow.common.exceptions.*;
import cloud.fogbow.common.models.CloudToken;
import cloud.fogbow.common.util.HomeDir;
import cloud.fogbow.common.util.PropertiesUtil;
import cloud.fogbow.ras.constants.SystemConstants;
import cloud.fogbow.ras.core.models.instances.VolumeInstance;
import cloud.fogbow.ras.core.models.orders.VolumeOrder;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.CloudStackHttpClient;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.CloudStackUrlMatcher;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.CloudStackUrlUtil;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.utils.URIBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.internal.verification.VerificationModeFactory;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@RunWith(PowerMockRunner.class)
@PrepareForTest({CloudStackUrlUtil.class, DeleteVolumeResponse.class, GetVolumeResponse.class})
public class CloudStackVolumePluginTest {

    private static final String CLOUD_NAME = "cloudstack";
    private static final String BASE_ENDPOINT_KEY = "cloudstack_api_url";
    private static final String REQUEST_FORMAT = "%s?command=%s";
    private static final String RESPONSE_FORMAT = "&response=%s";
    private static final String ID_FIELD = "&id=%s";
    private static final String EMPTY_INSTANCE = "";

    private static final int BYTE = 1;
    private static final int KILOBYTE = 1024 * BYTE;
    private static final int MEGABYTE = 1024 * KILOBYTE;
    private static final int GIGABYTE = 1024 * MEGABYTE;

    private static final String DEFAULT_STATE = "Ready";
    private static final String DEFAULT_DISPLAY_TEXT =
            "A description of the error will be shown if the success field is equal to false.";

    private static final String FAKE_DISK_OFFERING_ID = "fake-disk-offering-id";
    private static final String FAKE_ID = "fake-id";
    private static final String FAKE_JOB_ID = "fake-job-id";
    private static final String FAKE_NAME = "fake-name";
    private static final String FAKE_TAGS = "tag1:value1,tag2:value2";
    private static final String FAKE_TOKEN_PROVIDER = "fake-token-provider";
    private static final String FAKE_USER_ID = "fake-user-id";
    private static final String FAKE_TOKEN_VALUE = "fake-api-key:fake-secret-key";
    private static final String FAKE_MEMBER = "fake-member";
    private static final String FAKE_CLOUD_NAME = "cloud-name";

    private static final String JSON_FORMAT = "json";
    private static final String RESPONSE_KEY = "response";
    private static final String COMMAND_KEY = "command";
    private static final String DISK_OFFERING_ID_KEY = CreateVolumeRequest.DISK_OFFERING_ID;
    private static final String NAME_KEY = CreateVolumeRequest.VOLUME_NAME;
    private static final String SIZE_KEY = CreateVolumeRequest.VOLUME_SIZE;
    private static final String ZONE_ID_KEY = CreateVolumeRequest.ZONE_ID;

    private static final int COMPATIBLE_SIZE = 1;
    private static final int CUSTOMIZED_SIZE = 2;
    private static final int STANDARD_SIZE = 0;

    private CloudStackVolumePlugin plugin;
    private CloudStackHttpClient client;
    private CloudToken token;
    private Properties properties;

    @Before
    public void setUp() {
        String cloudStackConfFilePath = HomeDir.getPath() + SystemConstants.CLOUDS_CONFIGURATION_DIRECTORY_NAME +
                File.separator + CLOUD_NAME + File.separator + SystemConstants.CLOUD_SPECIFICITY_CONF_FILE_NAME;
        this.properties = PropertiesUtil.readProperties(cloudStackConfFilePath);
        this.client = Mockito.mock(CloudStackHttpClient.class);
        this.plugin = new CloudStackVolumePlugin(cloudStackConfFilePath);
        this.plugin.setClient(this.client);
        this.token = new CloudToken(FAKE_TOKEN_PROVIDER, FAKE_USER_ID, FAKE_TOKEN_VALUE);
    }

    // test case: When calling the requestInstance method with a size compatible with the
    // orchestrator's disk offering, HTTP GET requests must be made with a signed token, one to get
    // the compatible disk offering Id attached to the requisition, and another to create a volume
    // of compatible size, returning the id of the VolumeInstance object.
    @Test
    public void testCreateRequestInstanceSuccessfulWithDiskSizeCompatible()
            throws HttpResponseException, FogbowException {
        // set up
        PowerMockito.mockStatic(CloudStackUrlUtil.class);
        PowerMockito
                .when(CloudStackUrlUtil.createURIBuilder(Mockito.anyString(), Mockito.anyString()))
                .thenCallRealMethod();

        String urlFormat = REQUEST_FORMAT + RESPONSE_FORMAT;
        String baseEndpoint = getBaseEndpointFromCloudStackConf();
        String command = GetAllDiskOfferingsRequest.LIST_DISK_OFFERINGS_COMMAND;
        String jsonFormat = JSON_FORMAT;
        String request = String.format(urlFormat, baseEndpoint, command, jsonFormat);

        String id = FAKE_DISK_OFFERING_ID;
        int diskSize = COMPATIBLE_SIZE;
        boolean customized = false;
        String diskOfferings = getListDiskOfferrings(id, diskSize, customized, FAKE_TAGS);

        Mockito.when(this.client.doGetRequest(request, this.token)).thenReturn(diskOfferings);

        Map<String, String> expectedParams = new HashMap<>();
        expectedParams.put(COMMAND_KEY, CreateVolumeRequest.CREATE_VOLUME_COMMAND);
        expectedParams.put(RESPONSE_KEY, JSON_FORMAT);
        expectedParams.put(ZONE_ID_KEY, this.plugin.getZoneId());
        expectedParams.put(NAME_KEY, FAKE_NAME);
        expectedParams.put(DISK_OFFERING_ID_KEY, FAKE_DISK_OFFERING_ID);
        expectedParams.put(SIZE_KEY, new Long(diskSize * GIGABYTE).toString());

        CloudStackUrlMatcher urlMatcher = new CloudStackUrlMatcher(expectedParams, SIZE_KEY);

        String response = getCreateVolumeResponse(FAKE_ID, FAKE_JOB_ID);
        Mockito.when(
                this.client.doGetRequest(Mockito.argThat(urlMatcher), Mockito.eq(this.token)))
                .thenReturn(response);

        // exercise
        VolumeOrder order =
                new VolumeOrder(null, FAKE_MEMBER, FAKE_MEMBER, "default", FAKE_NAME, COMPATIBLE_SIZE);
        String volumeId = this.plugin.requestInstance(order, this.token);

        // verify
        PowerMockito.verifyStatic(CloudStackUrlUtil.class, VerificationModeFactory.times(2));
        CloudStackUrlUtil.sign(Mockito.any(URIBuilder.class), Mockito.anyString());

        Mockito.verify(this.client, Mockito.times(1)).doGetRequest(Mockito.eq(request),
                Mockito.eq(this.token));

        Mockito.verify(this.client, Mockito.times(1)).doGetRequest(Mockito.argThat(urlMatcher),
                Mockito.eq(this.token));

        String expectedId = FAKE_ID;
        Assert.assertEquals(expectedId, volumeId);
    }

    // test case: When calling the requestInstance method to get a size customized by the
    // orchestrator's disk offering, HTTP GET requests must be made with a signed token, one to get
    // the standard disk offering Id attached to the requisition, and another to create a volume of
    // customized size, returning the id of the VolumeInstance object.
    @Test
    public void testCreateRequestInstanceSuccessfulWithDiskSizeCustomized()
            throws HttpResponseException, FogbowException {
        // set up
        PowerMockito.mockStatic(CloudStackUrlUtil.class);
        PowerMockito
                .when(CloudStackUrlUtil.createURIBuilder(Mockito.anyString(), Mockito.anyString()))
                .thenCallRealMethod();

        String urlFormat = REQUEST_FORMAT + RESPONSE_FORMAT;
        String baseEndpoint = getBaseEndpointFromCloudStackConf();
        String command = GetAllDiskOfferingsRequest.LIST_DISK_OFFERINGS_COMMAND;
        String jsonFormat = JSON_FORMAT;
        String request = String.format(urlFormat, baseEndpoint, command, jsonFormat);

        String id = FAKE_DISK_OFFERING_ID;
        int diskSize = STANDARD_SIZE;
        boolean customized = true;
        String diskOfferings = getListDiskOfferrings(id, diskSize, customized, FAKE_TAGS);

        Mockito.when(this.client.doGetRequest(request, this.token)).thenReturn(diskOfferings);

        Map<String, String> expectedParams = new HashMap<>();
        expectedParams.put(COMMAND_KEY, CreateVolumeRequest.CREATE_VOLUME_COMMAND);
        expectedParams.put(RESPONSE_KEY, JSON_FORMAT);
        expectedParams.put(ZONE_ID_KEY, this.plugin.getZoneId());
        expectedParams.put(NAME_KEY, FAKE_NAME);
        expectedParams.put(DISK_OFFERING_ID_KEY, FAKE_DISK_OFFERING_ID);
        expectedParams.put(SIZE_KEY, new Long(diskSize * GIGABYTE).toString());

        CloudStackUrlMatcher urlMatcher = new CloudStackUrlMatcher(expectedParams, SIZE_KEY);

        String response = getCreateVolumeResponse(FAKE_ID, FAKE_JOB_ID);
        Mockito.when(
                this.client.doGetRequest(Mockito.argThat(urlMatcher), Mockito.eq(this.token)))
                .thenReturn(response);

        // exercise
        VolumeOrder order =
                new VolumeOrder(null, FAKE_MEMBER, FAKE_MEMBER, "default", FAKE_NAME, CUSTOMIZED_SIZE);
        String volumeId = this.plugin.requestInstance(order, this.token);

        // verify
        PowerMockito.verifyStatic(CloudStackUrlUtil.class, VerificationModeFactory.times(2));
        CloudStackUrlUtil.sign(Mockito.any(URIBuilder.class), Mockito.anyString());

        Mockito.verify(this.client, Mockito.times(1)).doGetRequest(Mockito.eq(request),
                Mockito.eq(this.token));

        Mockito.verify(this.client, Mockito.times(1)).doGetRequest(Mockito.argThat(urlMatcher),
                Mockito.eq(this.token));

        String expectedId = FAKE_ID;
        Assert.assertEquals(expectedId, volumeId);
    }

    @Test
    public void testCreateRequestInstanceSuccessfulWithRequirements() throws HttpResponseException, FogbowException {
        // set up
        PowerMockito.mockStatic(CloudStackUrlUtil.class);
        PowerMockito
                .when(CloudStackUrlUtil.createURIBuilder(Mockito.anyString(), Mockito.anyString()))
                .thenCallRealMethod();

        String urlFormat = REQUEST_FORMAT + RESPONSE_FORMAT;
        String baseEndpoint = getBaseEndpointFromCloudStackConf();
        String command = GetAllDiskOfferingsRequest.LIST_DISK_OFFERINGS_COMMAND;
        String jsonFormat = JSON_FORMAT;
        String request = String.format(urlFormat, baseEndpoint, command, jsonFormat);

        String id = FAKE_DISK_OFFERING_ID;
        int diskSize = COMPATIBLE_SIZE;
        boolean customized = false;
        String diskOfferings = getListDiskOfferrings(id, diskSize, customized, FAKE_TAGS);
        Map<String, String> fakeRequirements = new HashMap<>();
        fakeRequirements.put("tag1", "value1");

        Mockito.when(this.client.doGetRequest(request, this.token)).thenReturn(diskOfferings);

        Map<String, String> expectedParams = new HashMap<>();
        expectedParams.put(COMMAND_KEY, CreateVolumeRequest.CREATE_VOLUME_COMMAND);
        expectedParams.put(RESPONSE_KEY, JSON_FORMAT);
        expectedParams.put(ZONE_ID_KEY, this.plugin.getZoneId());
        expectedParams.put(NAME_KEY, FAKE_NAME);
        expectedParams.put(DISK_OFFERING_ID_KEY, FAKE_DISK_OFFERING_ID);
        expectedParams.put(SIZE_KEY, new Long(diskSize * GIGABYTE).toString());

        CloudStackUrlMatcher urlMatcher = new CloudStackUrlMatcher(expectedParams, SIZE_KEY);

        String response = getCreateVolumeResponse(FAKE_ID, FAKE_JOB_ID);
        Mockito.when(
                this.client.doGetRequest(Mockito.argThat(urlMatcher), Mockito.eq(this.token)))
                .thenReturn(response);

        // exercise
        VolumeOrder order =
                new VolumeOrder(null, FAKE_MEMBER, FAKE_MEMBER, FAKE_CLOUD_NAME, FAKE_NAME, COMPATIBLE_SIZE);
        order.setRequirements(fakeRequirements);
        String volumeId = this.plugin.requestInstance(order, this.token);

        // verify
        PowerMockito.verifyStatic(CloudStackUrlUtil.class, VerificationModeFactory.times(2));
        CloudStackUrlUtil.sign(Mockito.any(URIBuilder.class), Mockito.anyString());

        Mockito.verify(this.client, Mockito.times(1)).doGetRequest(Mockito.eq(request),
                Mockito.eq(this.token));

        Mockito.verify(this.client, Mockito.times(1)).doGetRequest(Mockito.argThat(urlMatcher),
                Mockito.eq(this.token));

        String expectedId = FAKE_ID;
        Assert.assertEquals(expectedId, volumeId);
    }

    @Test(expected = FogbowException.class)
    public void testCreateRequestInstanceFailNoRequirements() throws HttpResponseException, FogbowException {
        // set up
        PowerMockito.mockStatic(CloudStackUrlUtil.class);
        PowerMockito
                .when(CloudStackUrlUtil.createURIBuilder(Mockito.anyString(), Mockito.anyString()))
                .thenCallRealMethod();

        String urlFormat = REQUEST_FORMAT + RESPONSE_FORMAT;
        String baseEndpoint = getBaseEndpointFromCloudStackConf();
        String command = GetAllDiskOfferingsRequest.LIST_DISK_OFFERINGS_COMMAND;
        String jsonFormat = JSON_FORMAT;
        String request = String.format(urlFormat, baseEndpoint, command, jsonFormat);

        String id = FAKE_DISK_OFFERING_ID;
        int diskSize = COMPATIBLE_SIZE;
        boolean customized = false;
        String diskOfferings = getListDiskOfferrings(id, diskSize, customized, FAKE_TAGS);
        Map<String, String> fakeRequirements = new HashMap<>();
        fakeRequirements.put("tag3", "value3");

        Mockito.when(this.client.doGetRequest(request, this.token)).thenReturn(diskOfferings);

        Map<String, String> expectedParams = new HashMap<>();
        expectedParams.put(COMMAND_KEY, CreateVolumeRequest.CREATE_VOLUME_COMMAND);
        expectedParams.put(RESPONSE_KEY, JSON_FORMAT);
        expectedParams.put(ZONE_ID_KEY, this.plugin.getZoneId());
        expectedParams.put(NAME_KEY, FAKE_NAME);
        expectedParams.put(DISK_OFFERING_ID_KEY, FAKE_DISK_OFFERING_ID);
        expectedParams.put(SIZE_KEY, new Long(diskSize * GIGABYTE).toString());

        CloudStackUrlMatcher urlMatcher = new CloudStackUrlMatcher(expectedParams, SIZE_KEY);

        String response = getCreateVolumeResponse(FAKE_ID, FAKE_JOB_ID);
        Mockito.when(
                this.client.doGetRequest(Mockito.argThat(urlMatcher), Mockito.eq(this.token)))
                .thenReturn(response);

        // exercise
        VolumeOrder order =
                new VolumeOrder(null, FAKE_MEMBER, FAKE_MEMBER, FAKE_CLOUD_NAME, FAKE_NAME, COMPATIBLE_SIZE);
        order.setRequirements(fakeRequirements);
        String volumeId = this.plugin.requestInstance(order, this.token);

        // verify
        PowerMockito.verifyStatic(CloudStackUrlUtil.class, VerificationModeFactory.times(2));
        CloudStackUrlUtil.sign(Mockito.any(URIBuilder.class), Mockito.anyString());

        Mockito.verify(this.client, Mockito.times(1)).doGetRequest(Mockito.eq(request),
                Mockito.eq(this.token));
    }

    // test case: When calling the requestInstance method with a user without permission, an
    // UnauthorizedRequestException must be thrown.
    @Test(expected = UnauthorizedRequestException.class)
    public void testCreateRequestInstanceThrowUnauthorizedRequestException()
            throws FogbowException, HttpResponseException {

        // set up
        PowerMockito.mockStatic(CloudStackUrlUtil.class);
        PowerMockito
                .when(CloudStackUrlUtil.createURIBuilder(Mockito.anyString(), Mockito.anyString()))
                .thenCallRealMethod();

        Mockito.when(this.client.doGetRequest(Mockito.anyString(),
                Mockito.any(CloudToken.class)))
                .thenThrow(new HttpResponseException(HttpStatus.SC_FORBIDDEN, null));

        try {
            // exercise
            VolumeOrder order =
                    new VolumeOrder(null, FAKE_MEMBER, FAKE_MEMBER, "default", FAKE_NAME, CUSTOMIZED_SIZE);
            this.plugin.requestInstance(order, this.token);
        } finally {
            // verify
            PowerMockito.verifyStatic(CloudStackUrlUtil.class, VerificationModeFactory.times(1));
            CloudStackUrlUtil.sign(Mockito.any(URIBuilder.class), Mockito.anyString());

            Mockito.verify(this.client, Mockito.times(1)).doGetRequest(Mockito.anyString(),
                    Mockito.any(CloudToken.class));
        }
    }
    
    // test case: When try to request instance with an ID of the volume that do not exist, an
    // InstanceNotFoundException must be thrown.
    @Test(expected = InstanceNotFoundException.class)
    public void testCreateRequestInstanceThrowNotFoundException()
            throws HttpResponseException, FogbowException {
        // set up
        PowerMockito.mockStatic(CloudStackUrlUtil.class);
        PowerMockito
                .when(CloudStackUrlUtil.createURIBuilder(Mockito.anyString(), Mockito.anyString()))
                .thenCallRealMethod();

        Mockito.when(
                this.client.doGetRequest(Mockito.anyString(), Mockito.any(CloudToken.class)))
                .thenThrow(new HttpResponseException(HttpStatus.SC_NOT_FOUND, null));

        try {
            // exercise
            VolumeOrder order =
                    new VolumeOrder(null, FAKE_MEMBER, FAKE_MEMBER, "default", FAKE_NAME, CUSTOMIZED_SIZE);
            this.plugin.requestInstance(order, this.token);
        } finally {
            // verify
            PowerMockito.verifyStatic(CloudStackUrlUtil.class, VerificationModeFactory.times(1));
            CloudStackUrlUtil.sign(Mockito.any(URIBuilder.class), Mockito.anyString());

            Mockito.verify(this.client, Mockito.times(1)).doGetRequest(Mockito.anyString(),
                    Mockito.any(CloudToken.class));
        }
    }
    
    // test case: When calling the requestInstance method passing some invalid argument, an
    // FogbowException must be thrown.
    @Test(expected = FogbowException.class)
    public void testCreateRequestInstanceThrowFogbowException()
            throws HttpResponseException, FogbowException {
        // set up
        PowerMockito.mockStatic(CloudStackUrlUtil.class);
        PowerMockito
                .when(CloudStackUrlUtil.createURIBuilder(Mockito.anyString(), Mockito.anyString()))
                .thenCallRealMethod();

        Mockito.when(
                this.client.doGetRequest(Mockito.anyString(), Mockito.any(CloudToken.class)))
                .thenThrow(new HttpResponseException(HttpStatus.SC_BAD_REQUEST, null));

        try {
            // exercise
            VolumeOrder order =
                    new VolumeOrder(null, FAKE_MEMBER, FAKE_MEMBER, "default", FAKE_NAME, CUSTOMIZED_SIZE);
            this.plugin.requestInstance(order, this.token);
        } finally {
            // verify
            PowerMockito.verifyStatic(CloudStackUrlUtil.class, VerificationModeFactory.times(1));
            CloudStackUrlUtil.sign(Mockito.any(URIBuilder.class), Mockito.anyString());

            Mockito.verify(this.client, Mockito.times(1)).doGetRequest(Mockito.anyString(),
                    Mockito.any(CloudToken.class));
        }
    }
    
    // test case: When calling the requestInstance method with a unauthenticated user, an
    // UnauthenticatedUserException must be thrown.
    @Test(expected = UnauthenticatedUserException.class)
    public void testCreateRequestInstanceThrowUnauthenticatedUserException()
            throws FogbowException, HttpResponseException {

        // set up
        PowerMockito.mockStatic(CloudStackUrlUtil.class);
        PowerMockito
                .when(CloudStackUrlUtil.createURIBuilder(Mockito.anyString(), Mockito.anyString()))
                .thenCallRealMethod();

        Mockito.when(this.client.doGetRequest(Mockito.anyString(),
                Mockito.any(CloudToken.class)))
                .thenThrow(new HttpResponseException(HttpStatus.SC_UNAUTHORIZED, null));

        try {
            // exercise
            VolumeOrder order =
                    new VolumeOrder(null, FAKE_MEMBER, FAKE_MEMBER, "default", FAKE_NAME, CUSTOMIZED_SIZE);
            this.plugin.requestInstance(order, this.token);
        } finally {
            // verify
            PowerMockito.verifyStatic(CloudStackUrlUtil.class, VerificationModeFactory.times(1));
            CloudStackUrlUtil.sign(Mockito.any(URIBuilder.class), Mockito.anyString());

            Mockito.verify(this.client, Mockito.times(1)).doGetRequest(Mockito.anyString(),
                    Mockito.any(CloudToken.class));
        }
    }
    
    // test case: When calling the getInstance method, an HTTP GET request must be made with a
    // signed token, which returns a response in the JSON format for the retrieval of the
    // VolumeInstance object.
    @Test
    public void testGetInstanceRequestSuccessful()
            throws HttpResponseException, FogbowException {

        // set up
        PowerMockito.mockStatic(CloudStackUrlUtil.class);
        PowerMockito
                .when(CloudStackUrlUtil.createURIBuilder(Mockito.anyString(), Mockito.anyString()))
                .thenCallRealMethod();

        String urlFormat = REQUEST_FORMAT + RESPONSE_FORMAT + ID_FIELD;
        String baseEndpoint = getBaseEndpointFromCloudStackConf();
        String command = GetVolumeRequest.LIST_VOLUMES_COMMAND;
        String id = FAKE_ID;
        String jsonFormat = JSON_FORMAT;
        String request = String.format(urlFormat, baseEndpoint, command, jsonFormat, id);

        String name = FAKE_NAME;
        String size = new Long(COMPATIBLE_SIZE * GIGABYTE).toString();
        String state = DEFAULT_STATE;
        String volume = getVolumeResponse(id, name, size, state);
        String response = getListVolumesResponse(volume);

        Mockito.when(this.client.doGetRequest(request, this.token)).thenReturn(response);

        // exercise
        VolumeInstance recoveredInstance = this.plugin.getInstance(FAKE_ID, this.token);

        // verify
        PowerMockito.verifyStatic(CloudStackUrlUtil.class, VerificationModeFactory.times(1));
        CloudStackUrlUtil.sign(Mockito.any(URIBuilder.class), Mockito.anyString());

        Assert.assertEquals(id, recoveredInstance.getId());
        Assert.assertEquals(name, recoveredInstance.getName());
        Assert.assertEquals(COMPATIBLE_SIZE, recoveredInstance.getVolumeSize());

        Mockito.verify(this.client, Mockito.times(1)).doGetRequest(request, this.token);
    }

    // test case: When calling the getInstance method with a user without permission, an
    // UnauthorizedRequestException must be thrown.
    @Test(expected = UnauthorizedRequestException.class)
    public void testGetInstanceThrowUnauthorizedRequestException()
            throws UnexpectedException, FogbowException, HttpResponseException {

        // set up
        PowerMockito.mockStatic(CloudStackUrlUtil.class);
        PowerMockito
                .when(CloudStackUrlUtil.createURIBuilder(Mockito.anyString(), Mockito.anyString()))
                .thenCallRealMethod();

        Mockito.when(this.client.doGetRequest(Mockito.anyString(),
                Mockito.any(CloudToken.class)))
                .thenThrow(new HttpResponseException(HttpStatus.SC_FORBIDDEN, null));

        try {
            // exercise
            this.plugin.getInstance(FAKE_ID, this.token);
        } finally {
            // verify
            PowerMockito.verifyStatic(CloudStackUrlUtil.class, VerificationModeFactory.times(1));
            CloudStackUrlUtil.sign(Mockito.any(URIBuilder.class), Mockito.anyString());

            Mockito.verify(this.client, Mockito.times(1)).doGetRequest(Mockito.anyString(),
                    Mockito.any(CloudToken.class));
        }
    }

    // test case: When try to get an instance with an ID that does not exist, an
    // InstanceNotFoundException must be thrown.
    @Test(expected = InstanceNotFoundException.class)
    public void testGetInstanceThrowNotFoundException()
            throws HttpResponseException, FogbowException {
        // set up
        PowerMockito.mockStatic(CloudStackUrlUtil.class);
        PowerMockito
                .when(CloudStackUrlUtil.createURIBuilder(Mockito.anyString(), Mockito.anyString()))
                .thenCallRealMethod();

        Mockito.when(
                this.client.doGetRequest(Mockito.anyString(), Mockito.any(CloudToken.class)))
                .thenThrow(new HttpResponseException(HttpStatus.SC_NOT_FOUND, null));

        try {
            // exercise
            this.plugin.getInstance(FAKE_ID, this.token);
        } finally {
            // verify
            PowerMockito.verifyStatic(CloudStackUrlUtil.class, VerificationModeFactory.times(1));
            CloudStackUrlUtil.sign(Mockito.any(URIBuilder.class), Mockito.anyString());

            Mockito.verify(this.client, Mockito.times(1)).doGetRequest(Mockito.anyString(),
                    Mockito.any(CloudToken.class));
        }
    }
    
    // test case: When calling the getInstance method with a unauthenticated user, an
    // UnauthenticatedUserException must be thrown.
    @Test(expected = UnauthenticatedUserException.class)
    public void testGetInstanceThrowUnauthenticatedUserException()
            throws UnexpectedException, FogbowException, HttpResponseException {

        // set up
        PowerMockito.mockStatic(CloudStackUrlUtil.class);
        PowerMockito
                .when(CloudStackUrlUtil.createURIBuilder(Mockito.anyString(), Mockito.anyString()))
                .thenCallRealMethod();

        Mockito.when(this.client.doGetRequest(Mockito.anyString(),
                Mockito.any(CloudToken.class)))
                .thenThrow(new HttpResponseException(HttpStatus.SC_UNAUTHORIZED, null));

        try {
            // exercise
            this.plugin.getInstance(FAKE_ID, this.token);
        } finally {
            // verify
            PowerMockito.verifyStatic(CloudStackUrlUtil.class, VerificationModeFactory.times(1));
            CloudStackUrlUtil.sign(Mockito.any(URIBuilder.class), Mockito.anyString());

            Mockito.verify(this.client, Mockito.times(1)).doGetRequest(Mockito.anyString(),
                    Mockito.any(CloudToken.class));
        }
    }
    
    // test case: When calling the getInstance method passing some invalid argument, an
    // FogbowException must be thrown.
    @Test(expected = FogbowException.class)
    public void testGetInstanceThrowFogbowException()
            throws UnexpectedException, FogbowException, HttpResponseException {

        // set up
        PowerMockito.mockStatic(CloudStackUrlUtil.class);
        PowerMockito
                .when(CloudStackUrlUtil.createURIBuilder(Mockito.anyString(), Mockito.anyString()))
                .thenCallRealMethod();

        Mockito.when(
                this.client.doGetRequest(Mockito.anyString(), Mockito.any(CloudToken.class)))
                .thenThrow(new HttpResponseException(HttpStatus.SC_BAD_REQUEST, null));

        try {
            // exercise
            this.plugin.getInstance(FAKE_ID, this.token);
        } finally {
            // verify
            PowerMockito.verifyStatic(CloudStackUrlUtil.class, VerificationModeFactory.times(1));
            CloudStackUrlUtil.sign(Mockito.any(URIBuilder.class), Mockito.anyString());

            Mockito.verify(this.client, Mockito.times(1)).doGetRequest(Mockito.anyString(),
                    Mockito.any(CloudToken.class));
        }
    }
    
    // test case: When calling the getInstance method and an HTTP GET request returns a failure
    // response in JSON format, an UnexpectedException must be thrown.
    @Test(expected = UnexpectedException.class)
    public void testGetInstanceThrowUnexpectedException()
            throws HttpResponseException, FogbowException {
        // set up
        PowerMockito.mockStatic(CloudStackUrlUtil.class);
        PowerMockito
                .when(CloudStackUrlUtil.createURIBuilder(Mockito.anyString(), Mockito.anyString()))
                .thenCallRealMethod();

        String urlFormat = REQUEST_FORMAT + RESPONSE_FORMAT + ID_FIELD;
        String baseEndpoint = getBaseEndpointFromCloudStackConf();
        String command = GetVolumeRequest.LIST_VOLUMES_COMMAND;
        String id = FAKE_ID;
        String jsonFormat = JSON_FORMAT;
        String request = String.format(urlFormat, baseEndpoint, command, jsonFormat, id);

        String volume = EMPTY_INSTANCE;
        String response = getListVolumesResponse(volume);

        Mockito.when(this.client.doGetRequest(request, this.token)).thenReturn(response);

        PowerMockito.mockStatic(GetVolumeResponse.class);
        PowerMockito.when(GetVolumeResponse.fromJson(response)).thenCallRealMethod();

        try {
            // exercise
            this.plugin.getInstance(FAKE_ID, this.token);
        } finally {
            // verify
            PowerMockito.verifyStatic(CloudStackUrlUtil.class, VerificationModeFactory.times(1));
            CloudStackUrlUtil.sign(Mockito.any(URIBuilder.class), Mockito.anyString());

            Mockito.verify(this.client, Mockito.times(1)).doGetRequest(Mockito.anyString(),
                    Mockito.any(CloudToken.class));

            PowerMockito.verifyStatic(GetVolumeResponse.class, VerificationModeFactory.times(1));
            GetVolumeResponse.fromJson(Mockito.eq(response));
        }
    }

    // test case: When calling the deleteInstance method, an HTTP GET request must be made with a
    // signed token, which returns a response in the JSON format.
    @Test
    public void testDeleteInstanceRequestSuccessful()
            throws HttpResponseException, FogbowException {

        // set up
        PowerMockito.mockStatic(CloudStackUrlUtil.class);
        PowerMockito
                .when(CloudStackUrlUtil.createURIBuilder(Mockito.anyString(), Mockito.anyString()))
                .thenCallRealMethod();

        String urlFormat = REQUEST_FORMAT + RESPONSE_FORMAT + ID_FIELD;
        String baseEndpoint = getBaseEndpointFromCloudStackConf();
        String command = DeleteVolumeRequest.DELETE_VOLUME_COMMAND;
        String id = FAKE_ID;
        String jsonFormat = JSON_FORMAT;
        String request = String.format(urlFormat, baseEndpoint, command, jsonFormat, id);

        boolean success = true;
        String response = getDeleteVolumeResponse(success);

        Mockito.when(this.client.doGetRequest(request, this.token)).thenReturn(response);

        PowerMockito.mockStatic(DeleteVolumeResponse.class);
        PowerMockito.when(DeleteVolumeResponse.fromJson(response)).thenCallRealMethod();

        // exercise
        this.plugin.deleteInstance(FAKE_ID, this.token);

        // verify
        PowerMockito.verifyStatic(CloudStackUrlUtil.class, VerificationModeFactory.times(1));
        CloudStackUrlUtil.sign(Mockito.any(URIBuilder.class), Mockito.anyString());

        Mockito.verify(this.client, Mockito.times(1)).doGetRequest(request, token);

        PowerMockito.verifyStatic(DeleteVolumeResponse.class, VerificationModeFactory.times(1));
        DeleteVolumeResponse.fromJson(Mockito.eq(response));
    }

    // test case: When calling the deleteInstance method with a user without permission, an
    // UnauthorizedRequestException must be thrown.
    @Test(expected = UnauthorizedRequestException.class)
    public void testDeleteInstanceThrowUnauthorizedRequestException()
            throws UnexpectedException, FogbowException, HttpResponseException {

        // set up
        PowerMockito.mockStatic(CloudStackUrlUtil.class);
        PowerMockito
                .when(CloudStackUrlUtil.createURIBuilder(Mockito.anyString(), Mockito.anyString()))
                .thenCallRealMethod();

        Mockito.when(this.client.doGetRequest(Mockito.anyString(),
                Mockito.any(CloudToken.class)))
                .thenThrow(new HttpResponseException(HttpStatus.SC_FORBIDDEN, null));

        try {
            // exercise
            this.plugin.deleteInstance(FAKE_ID, this.token);
        } finally {
            // verify
            PowerMockito.verifyStatic(CloudStackUrlUtil.class, VerificationModeFactory.times(1));
            CloudStackUrlUtil.sign(Mockito.any(URIBuilder.class), Mockito.anyString());

            Mockito.verify(this.client, Mockito.times(1)).doGetRequest(Mockito.anyString(),
                    Mockito.any(CloudToken.class));
        }
    }

    // test case: When try to delete an instance with an ID that does not exist, an
    // InstanceNotFoundException must be thrown.
    @Test(expected = InstanceNotFoundException.class)
    public void testDeleteInstanceThrowNotFoundException()
            throws HttpResponseException, FogbowException {
        // set up
        PowerMockito.mockStatic(CloudStackUrlUtil.class);
        PowerMockito
                .when(CloudStackUrlUtil.createURIBuilder(Mockito.anyString(), Mockito.anyString()))
                .thenCallRealMethod();

        Mockito.when(
                this.client.doGetRequest(Mockito.anyString(), Mockito.any(CloudToken.class)))
                .thenThrow(new HttpResponseException(HttpStatus.SC_NOT_FOUND, null));

        try {
            // exercise
            this.plugin.deleteInstance(FAKE_ID, this.token);
        } finally {
            // verify
            PowerMockito.verifyStatic(CloudStackUrlUtil.class, VerificationModeFactory.times(1));
            CloudStackUrlUtil.sign(Mockito.any(URIBuilder.class), Mockito.anyString());

            Mockito.verify(this.client, Mockito.times(1)).doGetRequest(Mockito.anyString(),
                    Mockito.any(CloudToken.class));
        }
    }

    // test case: When calling the deleteInstance method with a unauthenticated user, an
    // UnauthenticatedUserException must be thrown.
    @Test(expected = UnauthenticatedUserException.class)
    public void testDeleteInstanceThrowUnauthenticatedUserException()
            throws UnexpectedException, FogbowException, HttpResponseException {

        // set up
        PowerMockito.mockStatic(CloudStackUrlUtil.class);
        PowerMockito
                .when(CloudStackUrlUtil.createURIBuilder(Mockito.anyString(), Mockito.anyString()))
                .thenCallRealMethod();

        Mockito.when(this.client.doGetRequest(Mockito.anyString(),
                Mockito.any(CloudToken.class)))
                .thenThrow(new HttpResponseException(HttpStatus.SC_UNAUTHORIZED, null));

        try {
            // exercise
            this.plugin.deleteInstance(FAKE_ID, this.token);
        } finally {
            // verify
            PowerMockito.verifyStatic(CloudStackUrlUtil.class, VerificationModeFactory.times(1));
            CloudStackUrlUtil.sign(Mockito.any(URIBuilder.class), Mockito.anyString());

            Mockito.verify(this.client, Mockito.times(1)).doGetRequest(Mockito.anyString(),
                    Mockito.any(CloudToken.class));
        }
    }
    
    // test case: When calling the deleteInstance method passing some invalid argument, an
    // FogbowException must be thrown.
    @Test(expected = FogbowException.class)
    public void testDeleteInstanceThrowFogbowException()
            throws UnexpectedException, FogbowException, HttpResponseException {

        // set up
        PowerMockito.mockStatic(CloudStackUrlUtil.class);
        PowerMockito
                .when(CloudStackUrlUtil.createURIBuilder(Mockito.anyString(), Mockito.anyString()))
                .thenCallRealMethod();

        Mockito.when(
                this.client.doGetRequest(Mockito.anyString(), Mockito.any(CloudToken.class)))
                .thenThrow(new HttpResponseException(HttpStatus.SC_BAD_REQUEST, null));

        try {
            // exercise
            this.plugin.deleteInstance(FAKE_ID, this.token);
        } finally {
            // verify
            PowerMockito.verifyStatic(CloudStackUrlUtil.class, VerificationModeFactory.times(1));
            CloudStackUrlUtil.sign(Mockito.any(URIBuilder.class), Mockito.anyString());

            Mockito.verify(this.client, Mockito.times(1)).doGetRequest(Mockito.anyString(),
                    Mockito.any(CloudToken.class));
        }
    }
    
    // test case: When calling the deleteInstance method and an HTTP GET request returns a failure
    // response in JSON format, an UnexpectedException must be thrown.
    @Test(expected = UnexpectedException.class)
    public void testDeleteInstanceThrowUnexpectedException()
            throws HttpResponseException, FogbowException {
        // set up
        PowerMockito.mockStatic(CloudStackUrlUtil.class);
        PowerMockito
                .when(CloudStackUrlUtil.createURIBuilder(Mockito.anyString(), Mockito.anyString()))
                .thenCallRealMethod();

        String urlFormat = REQUEST_FORMAT + RESPONSE_FORMAT + ID_FIELD;
        String baseEndpoint = getBaseEndpointFromCloudStackConf();
        String command = DeleteVolumeRequest.DELETE_VOLUME_COMMAND;
        String id = FAKE_ID;
        String jsonFormat = JSON_FORMAT;
        String request = String.format(urlFormat, baseEndpoint, command, jsonFormat, id);

        boolean success = false;
        String response = getDeleteVolumeResponse(success);

        Mockito.when(this.client.doGetRequest(request, this.token)).thenReturn(response);

        PowerMockito.mockStatic(DeleteVolumeResponse.class);
        PowerMockito.when(DeleteVolumeResponse.fromJson(response)).thenCallRealMethod();

        try {
            // exercise
            this.plugin.deleteInstance(FAKE_ID, this.token);
        } finally {
            // verify
            PowerMockito.verifyStatic(CloudStackUrlUtil.class, VerificationModeFactory.times(1));
            CloudStackUrlUtil.sign(Mockito.any(URIBuilder.class), Mockito.anyString());

            Mockito.verify(this.client, Mockito.times(1)).doGetRequest(Mockito.anyString(),
                    Mockito.any(CloudToken.class));

            PowerMockito.verifyStatic(DeleteVolumeResponse.class, VerificationModeFactory.times(1));
            DeleteVolumeResponse.fromJson(Mockito.eq(response));
        }
    }
    
    private String getBaseEndpointFromCloudStackConf() {
        return this.properties.getProperty(BASE_ENDPOINT_KEY);
    }

    private String getListDiskOfferrings(String id, int diskSize, boolean customized, String tags) {
        String response = "{\"listdiskofferingsresponse\":{" + "\"diskoffering\":[{"
                + "\"id\": \"%s\","
                + "\"disksize\": %s,"
                + "\"iscustomized\": %s,"
                + "\"tags\": \"%s\""
                + "}]}}";

        return String.format(response, id, diskSize, customized, tags);
    }

    private String getCreateVolumeResponse(String id, String jobId) {
        String response = "{\"createvolumeresponse\":{"
                + "\"id\": \"%s\", "
                + "\"jobid\": \"%s\""
                + "}}";

        return String.format(response, id, jobId);
    }

    private String getListVolumesResponse(String volume) {
        String response = "{\"listvolumesresponse\":{\"volume\":[%s]}}";

        return String.format(response, volume);
    }

    private String getVolumeResponse(String id, String name, String size, String state) {
        String response = "{\"id\":\"%s\","
                + "\"name\":\"%s\","
                + "\"size\":\"%s\","
                + "\"state\":\"%s\""
                + "}";

        return String.format(response, id, name, size, state);
    }

    private String getDeleteVolumeResponse(boolean success) {
        String value = String.valueOf(success);
        String response = "{\"deletevolumeresponse\":{"
                + "\"displaytext\": \"%s\","
                + "\"success\": \"%s\""
                + "}}";

        return String.format(response, DEFAULT_DISPLAY_TEXT, value);
    }

}