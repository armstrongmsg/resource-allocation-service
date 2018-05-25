package org.fogbowcloud.manager.core.manager.plugins;

import java.util.Map;
import org.fogbowcloud.manager.core.exceptions.UnauthenticatedException;
import org.fogbowcloud.manager.core.manager.plugins.identity.exceptions.TokenCreationException;
import org.fogbowcloud.manager.core.manager.plugins.identity.exceptions.TokenValueCreationException;
import org.fogbowcloud.manager.core.manager.plugins.identity.exceptions.UnauthorizedException;
import org.fogbowcloud.manager.core.models.Credential;
import org.fogbowcloud.manager.core.models.token.FederationUser;
import org.fogbowcloud.manager.core.models.token.Token;

public interface FederationIdentityPlugin {

    /**
     * Creates a token value based on the user's credentials.
     *
     * @param userCredentials
     * @return a String that is a token value used to make requests
     * @throws UnauthenticatedException
     * @throws TokenValueCreationException
     */
    public String createFederationTokenValue(Map<String, String> userCredentials)
        throws UnauthenticatedException, TokenValueCreationException;

    /**
     * Based on an access id recreates a Token containing the corresponding access id plus the user
     * name and some arbitrary information regarding the token.
     *
     * @return a Token for the corresponding accessId.
     * @throws UnauthenticatedException
     */
    public FederationUser getFederationUser(String federationTokenValue) throws UnauthenticatedException;

    /**
     * Verifies if the federationTokenValue is valid against the identity service.
     *
     * @param federationTokenValue
     * @return a boolean stating whether the token value is valid or not.
     */
    public boolean isValid(String federationTokenValue);

    /** @return an array of the required and optional fields for token creation. */
    public Credential[] getCredentials();

}
