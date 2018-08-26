package org.fogbowcloud.manager.core.plugins.behavior.mapper;

import org.fogbowcloud.manager.core.exceptions.FogbowManagerException;
import org.fogbowcloud.manager.core.exceptions.UnexpectedException;
import org.fogbowcloud.manager.core.models.tokens.FederationUserToken;
import org.fogbowcloud.manager.core.models.tokens.Token;

public interface FederationToLocalMapperPlugin {

    public Token map(FederationUserToken token) throws UnexpectedException, FogbowManagerException;
}
