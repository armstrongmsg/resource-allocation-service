package org.fogbowcloud.manager.core.plugins.cloud;

import org.fogbowcloud.manager.core.exceptions.FogbowManagerException;
import org.fogbowcloud.manager.core.exceptions.UnexpectedException;
import org.fogbowcloud.manager.core.models.images.Image;
import org.fogbowcloud.manager.core.models.tokens.Token;

import java.util.Map;

public interface ImagePlugin<T extends Token> {

    Map<String, String> getAllImages(T localUserAttributes) throws FogbowManagerException, UnexpectedException;

    Image getImage(String imageId, T localUserAttributes) throws FogbowManagerException, UnexpectedException;
}
