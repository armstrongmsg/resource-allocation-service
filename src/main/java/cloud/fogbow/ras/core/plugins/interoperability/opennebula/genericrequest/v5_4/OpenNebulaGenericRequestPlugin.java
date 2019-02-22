package cloud.fogbow.ras.core.plugins.interoperability.opennebula.genericrequest.v5_4;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.opennebula.client.Client;
import org.opennebula.client.OneResponse;

import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.InvalidParameterException;
import cloud.fogbow.common.models.CloudToken;
import cloud.fogbow.common.util.connectivity.GenericRequestResponse;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.core.plugins.interoperability.genericrequest.GenericRequestPlugin;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.OpenNebulaClientUtil;

public class OpenNebulaGenericRequestPlugin implements GenericRequestPlugin<CloudToken, OpenNebulaGenericRequest> {
	
	@Override
	public GenericRequestResponse redirectGenericRequest(OpenNebulaGenericRequest genericRequest, CloudToken cloudToken)
			throws FogbowException {
        
		OpenNebulaGenericRequest request = (OpenNebulaGenericRequest) genericRequest;
        if (request.getOneResource() == null || request.getOneMethod() == null) {
        	throw new InvalidParameterException(Messages.Exception.INVALID_PARAMETER);
		}

		Client client = OpenNebulaClientUtil.createClient(genericRequest.getUrl(), cloudToken.getTokenValue());
		OneResource oneResource = OneResource.getValueOf(request.getOneResource());
		Class resourceClassType = oneResource.getClassType();

		
		List classes = new ArrayList<>();
		List values = new ArrayList<>();

		Object instance = null;
		if (request.getResourceId() != null) {
			instance = oneResource.createInstance(Integer.parseInt(request.getResourceId()), client);
		} else {
			if (oneResource.equals(OneResource.CLIENT)) {
				instance = (Client) oneResource.createInstance(cloudToken.getTokenValue(), request.getUrl());
			}
			if (!request.getParameters().isEmpty()) {
				classes.add(Client.class);
				values.add(client);
			}
		}
		
		for (Map.Entry<String, String> entries : request.getParameters().entrySet()) {
			OneParameter oneParameter = OneParameter.getValueOf(entries.getKey());
			classes.add(oneParameter.getClassType());
			values.add(oneParameter.getValue(entries.getValue()));
		}
		
		Method method = OneGenericMethod.generate(resourceClassType, request.getOneMethod(), classes);
		OneResponse response = (OneResponse) OneGenericMethod.invoke(instance, method, values);

		return this.getOneGenericRequestResponse(response);
	}

	private OpenNebulaGenericRequestResponse getOneGenericRequestResponse(OneResponse oneResponse) {
	    boolean isError = oneResponse.isError();
		String message = isError ? oneResponse.getErrorMessage() : oneResponse.getMessage();

		OpenNebulaGenericRequestResponse response = new OpenNebulaGenericRequestResponse(message, isError);
		return response;
	}

}
