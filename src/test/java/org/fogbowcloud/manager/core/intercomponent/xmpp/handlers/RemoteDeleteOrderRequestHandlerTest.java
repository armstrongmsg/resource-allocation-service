package org.fogbowcloud.manager.core.intercomponent.xmpp.handlers;

import org.fogbowcloud.manager.core.exceptions.FogbowManagerException;
import org.fogbowcloud.manager.core.exceptions.UnexpectedException;
import org.fogbowcloud.manager.core.intercomponent.RemoteFacade;
import org.fogbowcloud.manager.core.intercomponent.xmpp.PacketSenderHolder;
import org.fogbowcloud.manager.core.intercomponent.xmpp.requesters.RemoteDeleteOrderRequest;
import org.fogbowcloud.manager.core.models.orders.ComputeOrder;
import org.fogbowcloud.manager.core.models.orders.Order;
import org.fogbowcloud.manager.core.models.tokens.FederationUser;
import org.jamppa.component.PacketSender;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.xmpp.packet.IQ;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@RunWith(PowerMockRunner.class)
@PrepareForTest({RemoteFacade.class, PacketSenderHolder.class})
public class RemoteDeleteOrderRequestHandlerTest {

    public static final String IQ_RESULT_FORMAT = "\n<iq type=\"result\" id=\"%s\" from=\"%s\"/>";

    public static final String IQ_ERROR_RESULT_FORMAT =
            "\n<iq type=\"error\" id=\"%s\" from=\"%s\">\n" +
                    "  <error code=\"500\" type=\"wait\">\n" +
                    "    <undefined-condition xmlns=\"urn:ietf:params:xml:ns:xmpp-stanzas\"/>\n" +
                    "    <text xmlns=\"urn:ietf:params:xml:ns:xmpp-stanzas\">Fogbow Manager exception</text>\n" +
                    "  </error>\n" +
                    "</iq>";


    private RemoteDeleteOrderRequestHandler remoteDeleteOrderRequestHandler;
    private PacketSender packetSender;

    private Order order;
    private RemoteFacade remoteFacade;

    @Before
    public void setUp() {
        this.remoteDeleteOrderRequestHandler = new RemoteDeleteOrderRequestHandler();

        this.packetSender = Mockito.mock(PacketSender.class);

        PowerMockito.mockStatic(PacketSenderHolder.class);
        BDDMockito.given(PacketSenderHolder.getPacketSender()).willReturn(this.packetSender);

        this.remoteFacade = Mockito.mock(RemoteFacade.class);

        PowerMockito.mockStatic(RemoteFacade.class);
        BDDMockito.given(RemoteFacade.getInstance()).willReturn(this.remoteFacade);
    }

    // test case: When calling the method handle passing a valid IQ object, it must create an OK
    // result IQ and return it.
    @Test
    public void testHandleWithValidIQ() throws FogbowManagerException, UnexpectedException {
        //set up
        Map<String, String> attributes = new HashMap<>();
        attributes.put("user-name", "fogbow");

        FederationUser federationUser = new FederationUser("fake-id", attributes);

        this.order = new ComputeOrder(federationUser, "requestingMember", "providingmember",
                1, 2, 3, "imageId", null, "publicKey", new ArrayList<>());

        IQ iq = RemoteDeleteOrderRequest.marshal(this.order);

        // exercise
        IQ result = this.remoteDeleteOrderRequestHandler.handle(iq);

        //verify
        String orderId = order.getId();
        String orderProvidingMember = order.getProvidingMember();
        String expected = String.format(IQ_RESULT_FORMAT, orderId, orderProvidingMember);
        Assert.assertEquals(expected, result.toString());
    }

    // test case: When an exception occurs while deleting, the method handle should return a response error
    @Test
    public void testHandleWhenExceptionIsThrown() throws FogbowManagerException, UnexpectedException {
        //set up
        this.order = new ComputeOrder(null, "requestingMember", "providingmember",
                1, 2, 3, "imageId", null, "publicKey", new ArrayList<>());

        Mockito.doThrow(new FogbowManagerException()).when(this.remoteFacade).deleteOrder(this.order.getId(),
                this.order.getFederationUser(), this.order.getType());

        IQ iq = RemoteDeleteOrderRequest.marshal(this.order);

        // exercise
        IQ result = this.remoteDeleteOrderRequestHandler.handle(iq);

        //verify
        Mockito.verify(this.remoteFacade, Mockito.times(1)).deleteOrder(this.order.getId(),
            this.order.getFederationUser(), this.order.getType());

        String orderId = order.getId();
        String orderProvidingMember = order.getProvidingMember();
        String expected = String.format(IQ_ERROR_RESULT_FORMAT, orderId, orderProvidingMember);
        Assert.assertEquals(expected, result.toString());
    }

}
