package org.fogbowcloud.manager.core.plugins.cloud;

import org.fogbowcloud.manager.core.exceptions.FogbowManagerException;
import org.fogbowcloud.manager.core.models.orders.NetworkOrder;
import org.fogbowcloud.manager.core.models.instances.NetworkInstance;
import org.fogbowcloud.manager.core.models.token.Token;

public interface NetworkPlugin {

	public String requestInstance(NetworkOrder networkOrder, Token localToken) throws FogbowManagerException;

	public NetworkInstance getInstance(String networkInstanceId, Token localToken) throws FogbowManagerException;

	public void deleteInstance(String networkInstanceId, Token localToken) throws FogbowManagerException;
	
}
