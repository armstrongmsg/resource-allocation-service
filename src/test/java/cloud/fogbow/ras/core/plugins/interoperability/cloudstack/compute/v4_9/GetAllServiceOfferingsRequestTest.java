package cloud.fogbow.ras.core.plugins.interoperability.cloudstack.compute.v4_9;

import cloud.fogbow.common.exceptions.InvalidParameterException;
import cloud.fogbow.common.util.connectivity.cloud.cloudstack.CloudStackUrlUtil;
import org.apache.http.client.utils.URIBuilder;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

public class GetAllServiceOfferingsRequestTest {

    private final String CLOUDSTACK_URL_DEFAULT = "http://localhost";

    // test case: create GetAllServiceOfferingsRequestUrl successfully
    @Test
    public void testCreateGetAllServiceOfferingsRequestUrl() throws InvalidParameterException {
        // set up
        URIBuilder uriBuilder = CloudStackUrlUtil.createURIBuilder(CLOUDSTACK_URL_DEFAULT,
                GetAllServiceOfferingsRequest.LIST_SERVICE_OFFERINGS_COMMAND);
        String urlExpected = uriBuilder.toString();

        // exercise
        GetAllServiceOfferingsRequest getAllServiceOfferingsRequest =
                new GetAllServiceOfferingsRequest.Builder().build(CLOUDSTACK_URL_DEFAULT);
        String getAllServiceOfferingsRequestUrl = getAllServiceOfferingsRequest.getUriBuilder().toString();

        // verify
        Assert.assertEquals(urlExpected, getAllServiceOfferingsRequestUrl);
    }

    // TODO(Chico) fix the Fogbow Commom code. The CloudStackRequest throws a
    // InvalidParameterException from the package java.security instead the FogbowException
    @Ignore
    // test case: trying create GetAllServiceOfferingsRequestUrl but it occur an error
    @Test(expected = InvalidParameterException.class)
    public void testCreateGetAllServiceOfferingsRequestWithError() throws InvalidParameterException {
        // exercise and verify
        new GetAllServiceOfferingsRequest.Builder().build(null);
    }
}