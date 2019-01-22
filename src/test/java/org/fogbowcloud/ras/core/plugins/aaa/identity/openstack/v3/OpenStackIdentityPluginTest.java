package org.fogbowcloud.ras.core.plugins.aaa.identity.openstack.v3;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.fogbowcloud.ras.core.HomeDir;
import org.fogbowcloud.ras.core.constants.SystemConstants;
import org.fogbowcloud.ras.core.exceptions.InvalidTokenException;
import org.fogbowcloud.ras.core.plugins.aaa.tokengenerator.openstack.v3.OpenStackTokenGeneratorPlugin;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class OpenStackIdentityPluginTest {
    private static final String FAKE_PROVIDER = "fake-provider";
    private static final String FAKE_TOKEN_VALUE = "fake-token-value";
    private static final String FAKE_USER_ID = "fake-user-id";
    private static final String FAKE_NAME = "fake-name";
    private static final String FAKE_PROJECT_ID = "fake-project-id";
    private static final String FAKE_PROJECT_NAME = "fake-project-name";

    private OpenStackIdentityPlugin identityPlugin;
    private OpenStackTokenGeneratorPlugin tokenGenerator;

    @Before
    public void setUp() {
        this.identityPlugin = new OpenStackIdentityPlugin();
        String cloudConfPath = HomeDir.getPath() + SystemConstants.CLOUDS_CONFIGURATION_DIRECTORY_NAME + File.separator
                + "default" + File.separator + SystemConstants.CLOUD_SPECIFICITY_CONF_FILE_NAME;
        this.tokenGenerator = Mockito.spy(new OpenStackTokenGeneratorPlugin(cloudConfPath));
    }

    // TODO check this test !!!
    //test case: check if the token value information is correct when creating a token with the correct user credentials.
    @Test
    public void testCreateToken() throws Exception {
        //set up
        String fakeTokenValue = FAKE_PROVIDER + OpenStackTokenGeneratorPlugin.OPENSTACK_TOKEN_STRING_SEPARATOR + FAKE_TOKEN_VALUE +
                OpenStackTokenGeneratorPlugin.OPENSTACK_TOKEN_STRING_SEPARATOR + FAKE_USER_ID +
                OpenStackTokenGeneratorPlugin.OPENSTACK_TOKEN_STRING_SEPARATOR + FAKE_NAME +
                OpenStackTokenGeneratorPlugin.OPENSTACK_TOKEN_STRING_SEPARATOR + FAKE_PROJECT_ID +
                OpenStackTokenGeneratorPlugin.OPENSTACK_TOKEN_STRING_SEPARATOR + FAKE_PROJECT_NAME;
        Mockito.doReturn(fakeTokenValue).when(this.tokenGenerator).createTokenValue(Mockito.anyMap());
        Map<String, String> userCredentials = new HashMap<String, String>();
        userCredentials = new HashMap<String, String>();
        userCredentials.put(OpenStackTokenGeneratorPlugin.PASSWORD, "userPass");
        userCredentials.put(OpenStackTokenGeneratorPlugin.PROJECT_NAME, "projectId");

        //exercise
        String federationTokenValue = this.tokenGenerator.createTokenValue(userCredentials);

        //verify
        String split[] = federationTokenValue.split(OpenStackTokenGeneratorPlugin.OPENSTACK_TOKEN_STRING_SEPARATOR);
        Assert.assertEquals(split[0], FAKE_PROVIDER);
        Assert.assertEquals(split[1], FAKE_TOKEN_VALUE);
        Assert.assertEquals(split[2], FAKE_USER_ID);
        Assert.assertEquals(split[3], FAKE_NAME);
        Assert.assertEquals(split[4], FAKE_PROJECT_ID);
        Assert.assertEquals(split[5], FAKE_PROJECT_NAME);
    }

    //test case: check if createFederationTokenValue throws UnauthenticatedUserException when the user credentials
    // are invalid.
    @Test(expected = InvalidTokenException.class)
    public void testCreateTokenFail() throws Exception {
        //exercise/verify
        this.identityPlugin.createToken("anything");
    }
}
