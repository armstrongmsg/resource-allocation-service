package cloud.fogbow.ras.core.plugins.interoperability.openstack.genericrequest.v2;

import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.InvalidParameterException;
import cloud.fogbow.common.models.CloudToken;
import cloud.fogbow.common.util.connectivity.HttpRequestUtil;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.core.plugins.interoperability.genericrequest.GenericRequest;
import cloud.fogbow.ras.core.plugins.interoperability.genericrequest.GenericRequestHttpResponse;
import cloud.fogbow.ras.core.plugins.interoperability.genericrequest.HttpBasedGenericRequestPlugin;
import cloud.fogbow.ras.util.connectivity.AuditableHttpRequestClient;

import java.util.Map;

public class OpenStackGenericRequestPlugin extends HttpBasedGenericRequestPlugin {
    @Override
    public GenericRequestHttpResponse redirectGenericRequest(GenericRequest genericRequest, CloudToken token)
            throws FogbowException {
        Map<String, String> headers = genericRequest.getHeaders();

        if (headers.containsKey(HttpRequestUtil.X_AUTH_TOKEN_KEY)) {
            throw new InvalidParameterException(Messages.Exception.TOKEN_ALREADY_SPECIFIED);
        }

        headers.put(HttpRequestUtil.X_AUTH_TOKEN_KEY, token.getTokenValue());
        return getClient().doGenericRequest(genericRequest.getMethod(), genericRequest.getUrl(),
                headers, genericRequest.getBody(), token);
    }

    @Override
    protected void setClient(AuditableHttpRequestClient auditableHttpRequestClient) {
        super.setClient(auditableHttpRequestClient);
    }
}
