package org.fogbowcloud.manager.core.plugins.cloud.cloudstack;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.Charsets;
import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.exceptions.InvalidParameterException;
import org.fogbowcloud.manager.core.exceptions.UnauthorizedRequestException;
import org.fogbowcloud.manager.core.models.tokens.generators.cloudstack.CloudStackTokenGenerator;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.Map.Entry;
import java.util.TreeMap;

public class CloudStackUrlUtil {
    private static final Logger LOGGER = Logger.getLogger(CloudStackUrlUtil.class);

    private static final String COMMAND = "command";
    private static final String JSON = "json";
    private static final String RESPONSE_FORMAT = "response";
    private static final String SIGNATURE = "signature";

    public static void sign(URIBuilder requestEndpoint, String tokenValue) throws UnauthorizedRequestException {
        String[] tokenValueSplit = tokenValue.split(CloudStackTokenGenerator.TOKEN_VALUE_SEPARATOR);
        String apiKey = tokenValueSplit[0];
        String secretKey = tokenValueSplit[1];

        requestEndpoint.addParameter(CloudStackTokenGenerator.API_KEY, apiKey);
        requestEndpoint.addParameter(RESPONSE_FORMAT, JSON);

        String query = null;
        try {
            query = requestEndpoint.toString().substring(
                    requestEndpoint.toString().indexOf("?") + 1);
        } catch (IndexOutOfBoundsException e) {
            LOGGER.warn("Couldn't generate signature.", e);
            throw new UnauthorizedRequestException();
        }

        String[] querySplit = query.split("&");
        TreeMap<String, String> queryParts = new TreeMap<String, String>();
        for (String queryPart : querySplit) {
            String[] queryPartSplit = queryPart.split("=");
            queryParts.put(queryPartSplit[0].toLowerCase(),
                    queryPartSplit[1].toLowerCase());
        }

        StringBuilder orderedQuery = new StringBuilder();
        for (Entry<String, String> queryPartEntry : queryParts.entrySet()) {
            if (orderedQuery.length() > 0) {
                orderedQuery.append("&");
            }
            orderedQuery.append(queryPartEntry.getKey()).append("=")
                    .append(queryPartEntry.getValue());
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            byte[] secretKeyBytes = secretKey.getBytes(Charsets.UTF_8);
            Key key = new SecretKeySpec(secretKeyBytes, 0, secretKeyBytes.length, "HmacSHA1");
            mac.init(key);
            String signature = Base64.encodeBase64String(
                    mac.doFinal(orderedQuery.toString().getBytes(Charsets.UTF_8)));

            requestEndpoint.addParameter(SIGNATURE, signature);
        } catch (Exception e) {
            LOGGER.warn("Couldn't generate signature.", e);
            throw new UnauthorizedRequestException();
        }
    }


    public static URIBuilder createURIBuilder(String endpoint, String command) throws InvalidParameterException {
        try {
            URIBuilder uriBuilder = new URIBuilder(endpoint);
            uriBuilder.addParameter(COMMAND, command);
            return uriBuilder;
        } catch (Exception e) {
            String message = "Irregular syntax.";
            throw new InvalidParameterException(message);
        }
    }
}
