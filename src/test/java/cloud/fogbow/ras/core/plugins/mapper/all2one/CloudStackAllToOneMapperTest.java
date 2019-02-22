package cloud.fogbow.ras.core.plugins.mapper.all2one;

import cloud.fogbow.as.core.tokengenerator.plugins.AttributeJoiner;
import cloud.fogbow.as.core.tokengenerator.plugins.cloudstack.CloudStackTokenGeneratorPlugin;
import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.models.CloudToken;
import cloud.fogbow.common.models.FederationUser;
import cloud.fogbow.common.util.HomeDir;
import cloud.fogbow.ras.constants.ConfigurationPropertyKeys;
import cloud.fogbow.ras.constants.SystemConstants;
import cloud.fogbow.ras.core.PropertiesHolder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@RunWith(PowerMockRunner.class)
@PrepareForTest({CloudStackTokenGeneratorPlugin.class})
public class CloudStackAllToOneMapperTest {
    private static final String FAKE_LOGIN1 = "fake-login1";
    private static final String FAKE_LOGIN2 = "fake-login2";
    private static final String FAKE_PASSWORD_KEY = "password";
    private static final String FAKE_PASSWORD = "fake-password";
    private static final String FAKE_USER_ID = "fake-user-id";
    private static final String FAKE_USER_NAME_KEY = "username";
    private static final String FAKE_USER_NAME = "fake-user-name";
    private static final String FAKE_TOKEN_VALUE = "fake-api-key:fake-secret-key";

    private String memberId;
    private CloudStackAllToOneMapper mapper;
    private CloudStackTokenGeneratorPlugin cloudStackTokenGenerator;

    @Before
    public void setUp() {
        String path = HomeDir.getPath();
        String mapperConfPath = path + SystemConstants.CLOUDS_CONFIGURATION_DIRECTORY_NAME + File.separator
                + "cloudstack" + File.separator + SystemConstants.MAPPER_CONF_FILE_NAME;

        this.memberId = PropertiesHolder.getInstance().getProperty(ConfigurationPropertyKeys.LOCAL_MEMBER_ID_KEY);
        this.cloudStackTokenGenerator = Mockito.spy(CloudStackTokenGeneratorPlugin.class);
        this.mapper = new CloudStackAllToOneMapper(mapperConfPath);
        this.mapper.setTokenGeneratorPlugin(this.cloudStackTokenGenerator);
    }

    //test case: two different Federation Tokens should be mapped to the same Local Token
    @Test
    public void testCreate2Tokens() throws FogbowException {
        //set up
        Map<String, String> userCredentials1 = new HashMap<String, String>();
        userCredentials1.put(FAKE_USER_NAME_KEY, FAKE_LOGIN1);
        userCredentials1.put(FAKE_PASSWORD_KEY, FAKE_PASSWORD);
        FederationUser token1 = new FederationUser(userCredentials1);

        Map<String, String> userCredentials2 = new HashMap<String, String>();
        userCredentials2.put(FAKE_USER_NAME_KEY, FAKE_LOGIN2);
        userCredentials2.put(FAKE_PASSWORD_KEY, FAKE_PASSWORD);
        FederationUser token2 = new FederationUser(userCredentials2);

        Map<String, String> attributes = new HashMap();
        attributes.put("provider", this.memberId);
        attributes.put("id", FAKE_USER_ID);
        attributes.put("name", FAKE_USER_NAME);
        attributes.put("token", FAKE_TOKEN_VALUE);
        String tokenValue = AttributeJoiner.join(attributes);

        Mockito.doReturn(tokenValue).when(this.cloudStackTokenGenerator).createTokenValue(Mockito.anyMap());

        //exercise
        CloudToken mappedToken1 = this.mapper.map(token1);
        CloudToken mappedToken2 = this.mapper.map(token2);

        //verify
        Assert.assertNotEquals(token1.getAttributes(), token2.getAttributes());
        Assert.assertEquals(mappedToken1.getUserId(), mappedToken2.getUserId());
        Assert.assertEquals(mappedToken1.getTokenValue(), mappedToken2.getTokenValue());
    }
}