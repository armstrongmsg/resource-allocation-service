package org.fogbowcloud.manager.core.models.tokens.generators.openstack.v3;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;
import org.fogbowcloud.manager.core.HomeDir;
import org.fogbowcloud.manager.core.PropertiesHolder;
import org.fogbowcloud.manager.core.constants.ConfigurationConstants;
import org.fogbowcloud.manager.core.exceptions.*;
import org.fogbowcloud.manager.util.connectivity.HttpRequestClientUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class KeystoneV3TokenGeneratorPluginTest {
    private static final String FAKE_USER_ID = "fake-user-id";
    private static final String FAKE_USER_NAME = "fake-user-name";
    private static final String FAKE_PROJECT_ID = "fake-project-id";
    private static final String FAKE_PROJECT_NAME = "fake-project-name";
    private static final String FAKE_TOKEN_VALUE = "fake-token-value";
    private static final String UTF_8 = "UTF-8";

    private HttpClient client;
    private HttpRequestClientUtil httpRequestClientUtil;
    private KeystoneV3TokenGeneratorPlugin keystoneV3TokenGenerator;
    private String memberId;

    @Before
    public void setUp() {
        this.client = Mockito.spy(HttpClient.class);
        this.httpRequestClientUtil = Mockito.spy(new HttpRequestClientUtil(this.client));
        this.keystoneV3TokenGenerator = Mockito.spy(new KeystoneV3TokenGeneratorPlugin());
        this.keystoneV3TokenGenerator.setClient(this.httpRequestClientUtil);
        this.memberId = PropertiesHolder.getInstance().getProperty(ConfigurationConstants.LOCAL_MEMBER_ID);
    }

    //test case: createTokenValue with valid credentials should generate a string with the appropriate values
    @Test
    public void testCreateTokenValueValidCredentials() throws IOException, FogbowManagerException,
            UnexpectedException {
        //set up
        String jsonResponse = "{\"token\":{\"user\":{\"id\":\"" + FAKE_USER_ID + "\",\"name\": \"" + FAKE_USER_NAME +
                "\"}, \"project\":{\"id\": \"" + FAKE_PROJECT_ID + "\", \"name\": \"" + FAKE_PROJECT_NAME +
                "\"}}}";
        HttpResponse httpResponse = Mockito.mock(HttpResponse.class);
        HttpEntity httpEntity = Mockito.mock(HttpEntity.class);
        InputStream contentInputStream = new ByteArrayInputStream(jsonResponse.getBytes(UTF_8));
        Mockito.when(httpEntity.getContent()).thenReturn(contentInputStream);
        Mockito.when(httpResponse.getEntity()).thenReturn(httpEntity);
        BasicStatusLine basicStatus = new BasicStatusLine(new ProtocolVersion("", 0, 0),
                HttpStatus.SC_OK, "");
        Mockito.when(httpResponse.getStatusLine()).thenReturn(basicStatus);
        Header[] headers = new BasicHeader[1];
        headers[0] = new BasicHeader(KeystoneV3TokenGeneratorPlugin.X_SUBJECT_TOKEN, FAKE_TOKEN_VALUE);
        Mockito.when(httpResponse.getAllHeaders()).thenReturn(headers);
        Mockito.when(this.client.execute(Mockito.any(HttpPost.class))).thenReturn(httpResponse);

        Map<String, String> userCredentials = new HashMap<String, String>();
        userCredentials.put(KeystoneV3TokenGeneratorPlugin.USER_ID, FAKE_USER_ID);
        userCredentials.put(KeystoneV3TokenGeneratorPlugin.PASSWORD, "any password");
        userCredentials.put(KeystoneV3TokenGeneratorPlugin.PROJECT_ID, FAKE_PROJECT_ID);

        //exercise
        String tokenValue = this.keystoneV3TokenGenerator.createTokenValue(userCredentials);

        //verify
        String split[] = tokenValue.split(KeystoneV3TokenGeneratorPlugin.TOKEN_VALUE_SEPARATOR);
        Assert.assertEquals(split.length,6);
        Assert.assertEquals(split[0], memberId);
        Assert.assertEquals(split[1], FAKE_TOKEN_VALUE);
        Assert.assertEquals(split[2], FAKE_USER_ID);
        Assert.assertEquals(split[3], FAKE_USER_NAME);
        Assert.assertEquals(split[4], FAKE_PROJECT_ID);
        Assert.assertEquals(split[5], FAKE_PROJECT_NAME);
    }

    //test case: createTokenValue with invalid credentials should throw FogbowManagerException
    @Test (expected = FogbowManagerException.class)
    public void testCreateTokenValueIncorrectCredentials() throws UnexpectedException, FogbowManagerException,
            IOException {
        String jsonResponse = "{\"token\":{\"user\":{\"id\":\"" + FAKE_USER_ID + "\",\"name\": \"" + FAKE_USER_NAME +
                "\"}, \"project\":{\"id\": \"" + FAKE_PROJECT_ID + "\", \"name\": \"" + FAKE_PROJECT_NAME +
                "\"}}}";
        HttpResponse httpResponse = Mockito.mock(HttpResponse.class);
        HttpEntity httpEntity = Mockito.mock(HttpEntity.class);
        InputStream contentInputStream = new ByteArrayInputStream(jsonResponse.getBytes(UTF_8));
        Mockito.when(httpEntity.getContent()).thenReturn(contentInputStream);
        Mockito.when(httpResponse.getEntity()).thenReturn(httpEntity);
        BasicStatusLine basicStatus = new BasicStatusLine(new ProtocolVersion("", 0, 0),
                HttpStatus.SC_UNAUTHORIZED, "");
        Mockito.when(httpResponse.getStatusLine()).thenReturn(basicStatus);
        Header[] headers = new BasicHeader[1];
        headers[0] = new BasicHeader(KeystoneV3TokenGeneratorPlugin.X_SUBJECT_TOKEN, FAKE_TOKEN_VALUE);
        Mockito.when(httpResponse.getAllHeaders()).thenReturn(headers);
        Mockito.when(this.client.execute(Mockito.any(HttpPost.class))).thenReturn(httpResponse);

        Map<String, String> userCredentials = new HashMap<String, String>();
        userCredentials.put(KeystoneV3TokenGeneratorPlugin.USER_ID, FAKE_USER_ID);
        userCredentials.put(KeystoneV3TokenGeneratorPlugin.PASSWORD, "any password");
        userCredentials.put(KeystoneV3TokenGeneratorPlugin.PROJECT_ID, FAKE_PROJECT_ID);

        //exercise
        String tokenValue = this.keystoneV3TokenGenerator.createTokenValue(userCredentials);
    }
}
