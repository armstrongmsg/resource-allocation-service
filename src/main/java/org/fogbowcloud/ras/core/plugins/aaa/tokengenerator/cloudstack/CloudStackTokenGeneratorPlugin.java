package org.fogbowcloud.ras.core.plugins.aaa.tokengenerator.cloudstack;

import org.apache.http.client.HttpResponseException;
import org.apache.log4j.Logger;
import org.fogbowcloud.ras.core.PropertiesHolder;
import org.fogbowcloud.ras.core.constants.ConfigurationConstants;
import org.fogbowcloud.ras.core.exceptions.FogbowRasException;
import org.fogbowcloud.ras.core.exceptions.InvalidParameterException;
import org.fogbowcloud.ras.core.exceptions.UnexpectedException;
import org.fogbowcloud.ras.core.models.tokens.Token;
import org.fogbowcloud.ras.core.plugins.aaa.tokengenerator.TokenGeneratorPlugin;
import org.fogbowcloud.ras.core.plugins.interoperability.cloudstack.CloudStackHttpToFogbowRasExceptionMapper;
import org.fogbowcloud.ras.util.connectivity.HttpRequestClientUtil;

import java.util.Map;

public class CloudStackTokenGeneratorPlugin implements TokenGeneratorPlugin {
    private static final Logger LOGGER = Logger.getLogger(CloudStackTokenGeneratorPlugin.class);

    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String DOMAIN = "domain";
    public static final String API_KEY = "apikey";
    public static final String CLOUDSTACK_URL = "cloudstack_api_url";
    public static final String TOKEN_VALUE_SEPARATOR = ":";
    public static final String TOKEN_STRING_SEPARATOR = "!#!";

    private String tokenProviderId;
    private HttpRequestClientUtil client;

    public CloudStackTokenGeneratorPlugin() {
        this.tokenProviderId = PropertiesHolder.getInstance().getProperty(ConfigurationConstants.LOCAL_MEMBER_ID);
        this.client = new HttpRequestClientUtil();
    }

    @Override
    public String createTokenValue(Map<String, String> credentials) throws FogbowRasException, UnexpectedException {
        if ((credentials == null) || (credentials.get(USERNAME) == null) || (credentials.get(PASSWORD) == null) ||
             credentials.get(DOMAIN) == null) {
            String errorMsg = "User credentials can't be null";
            throw new InvalidParameterException(errorMsg);
        }

        LoginRequest request = createLoginRequest(credentials);
        HttpRequestClientUtil.Response jsonResponse = null;
        try {
            // NOTE(pauloewerton): since all cloudstack requests params are passed via url args, we do not need to
            // send a valid json body in the post request
            jsonResponse = this.client.doPostRequest(request.getUriBuilder().toString(), "data");
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowRasExceptionMapper.map(e);
        }

        LoginResponse response = LoginResponse.fromJson(jsonResponse.getContent());
        String tokenValue = getTokenValue(response.getSessionKey());

        return tokenValue;
    }

    private LoginRequest createLoginRequest(Map<String, String> credentials) throws InvalidParameterException {
        String userId = credentials.get(USERNAME);
        String password = credentials.get(PASSWORD);
        String domain = credentials.get(DOMAIN);

        LoginRequest loginRequest = new LoginRequest.Builder()
                .username(userId)
                .password(password)
                .domain(domain)
                .build();

        return loginRequest;
    }

    private String getTokenValue(String sessionKey) throws FogbowRasException, UnexpectedException {
        ListAccountsRequest request = new ListAccountsRequest.Builder()
                .sessionKey(sessionKey)
                .build();

        String jsonResponse = null;
        try {
            // NOTE(pauloewerton): passing a placeholder as there is no need to pass a valid token in this request
            jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), new Token("CloudStackTokenValue"));
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowRasExceptionMapper.map(e);
        }

        String tokenString = null;
        try {
            ListAccountsResponse response = ListAccountsResponse.fromJson(jsonResponse);
            // NOTE(pauloewerton): considering only one account/user per request
            ListAccountsResponse.User user = response.getAccounts().get(0).getUsers().get(0);

            // NOTE(pauloewerton): keeping a colon as separator as expected by the other cloudstack plugins
            String tokenValue = user.getApiKey() + TOKEN_VALUE_SEPARATOR + user.getSecretKey();
            String userId = user.getId();
            String firstName = user.getFirstName();
            String lastName = user.getLastName();
            String userName = (firstName != null && lastName != null) ? firstName + " " + lastName : user.getUsername();

            tokenString = this.tokenProviderId + TOKEN_STRING_SEPARATOR + tokenValue + TOKEN_STRING_SEPARATOR +
                          userId + TOKEN_STRING_SEPARATOR + userName;
        } catch(Exception e) {
            LOGGER.error("Exception while getting token from json", e);
            throw new UnexpectedException("Exception while getting token from json", e);
        }

        return tokenString; }
    // Used for testing
    public void setClient(HttpRequestClientUtil client) {
        this.client = client;
    }
}
