package org.fogbowcloud.manager.core.intercomponent.xmpp.requesters;

import java.util.HashMap;
import java.util.Map;

import org.dom4j.Element;
import org.fogbowcloud.manager.core.exceptions.InvalidParameterException;
import org.fogbowcloud.manager.core.exceptions.UnauthorizedRequestException;
import org.fogbowcloud.manager.core.exceptions.UnavailableProviderException;
import org.fogbowcloud.manager.core.exceptions.UnexpectedException;
import org.fogbowcloud.manager.core.intercomponent.xmpp.IQMatcher;
import org.fogbowcloud.manager.core.intercomponent.xmpp.IqElement;
import org.fogbowcloud.manager.core.intercomponent.xmpp.PacketSenderHolder;
import org.fogbowcloud.manager.core.intercomponent.xmpp.RemoteMethod;
import org.fogbowcloud.manager.core.models.ResourceType;
import org.fogbowcloud.manager.core.models.quotas.ComputeQuota;
import org.fogbowcloud.manager.core.models.quotas.Quota;
import org.fogbowcloud.manager.core.models.quotas.allocation.ComputeAllocation;
import org.fogbowcloud.manager.core.models.tokens.FederationUser;
import org.jamppa.component.PacketSender;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

import com.google.gson.Gson;

public class RemoteGetUserQuotaRequestTest {
	
 	private RemoteGetUserQuotaRequest remoteGetUserQuotaRequest;
	private PacketSender packetSender;
	
	private Quota quota;
	private String provider;
	private FederationUser federationUser;
	private ResourceType resourceType;
	
 	@Before
	public void setUp() throws InvalidParameterException {
		Map<String, String> attributes = new HashMap<String, String>();
 		attributes.put("user-name", "user-name");
 		this.federationUser = new FederationUser("federation-user-id", attributes);
 		this.provider = "provider";
 		this.resourceType = ResourceType.COMPUTE;
		this.remoteGetUserQuotaRequest = new RemoteGetUserQuotaRequest(this.provider, this.federationUser, this.resourceType);
		this.packetSender = Mockito.mock(PacketSender.class);
		PacketSenderHolder.init(packetSender);
		ComputeAllocation computeAllocation = new ComputeAllocation(10,  20,  30);
		ComputeAllocation usedQuota = new ComputeAllocation(40, 50, 60);
		this.quota = new ComputeQuota(computeAllocation, usedQuota);
	}
 	
 	//test case: checks if IQ attributes is according to both RemoteGetUserQuotaRequest constructor parameters 
 	//and remote get user quota request rules. In addition, it checks if the instance from a possible response is 
 	//properly created and returned by the "send" method
	@Test
	public void testSend() throws Exception {
		//set up
 		IQ iqResponse = getQuotaIQResponse(this.quota);
 		Mockito.doReturn(iqResponse).when(this.packetSender).syncSendPacket(Mockito.any(IQ.class));
 		IQ expectedIQ = RemoteGetUserQuotaRequest.marshal(this.provider, this.federationUser, this.resourceType);
 		
 		//exercise
 		Quota responseQuota = this.remoteGetUserQuotaRequest.send();
		
 		//verify
		IQMatcher matcher = new IQMatcher(expectedIQ);
		Mockito.verify(this.packetSender).syncSendPacket(Mockito.argThat(matcher));
		
		ComputeAllocation expectedComputeAvailableQuota = (ComputeAllocation) this.quota.getAvailableQuota();
		ComputeAllocation actualComputeAvailableQuota = (ComputeAllocation) responseQuota.getAvailableQuota();
		Assert.assertEquals(expectedComputeAvailableQuota.getRam(), actualComputeAvailableQuota.getRam());
		Assert.assertEquals(expectedComputeAvailableQuota.getInstances(), actualComputeAvailableQuota.getInstances());
		Assert.assertEquals(expectedComputeAvailableQuota.getvCPU(), actualComputeAvailableQuota.getvCPU());
		
		ComputeAllocation expectedComputeUsedQuota = (ComputeAllocation) this.quota.getUsedQuota();
		ComputeAllocation actualComputeUsedQuota = (ComputeAllocation) responseQuota.getUsedQuota();
		Assert.assertEquals(expectedComputeUsedQuota.getRam(), actualComputeUsedQuota.getRam());
		Assert.assertEquals(expectedComputeUsedQuota.getInstances(), actualComputeUsedQuota.getInstances());
		Assert.assertEquals(expectedComputeUsedQuota.getvCPU(), actualComputeUsedQuota.getvCPU());
		
		ComputeAllocation expectedComputeTotalQuota = (ComputeAllocation) this.quota.getTotalQuota();
		ComputeAllocation actualComputeTotalQuota = (ComputeAllocation) responseQuota.getTotalQuota();
		Assert.assertEquals(expectedComputeTotalQuota.getRam(), actualComputeTotalQuota.getRam());
		Assert.assertEquals(expectedComputeTotalQuota.getInstances(), actualComputeTotalQuota.getInstances());
		Assert.assertEquals(expectedComputeTotalQuota.getvCPU(), actualComputeTotalQuota.getvCPU());
	}
	
	//test case: checks if "send" is properly forwading UnavailableProviderException thrown by 
	//"XmppErrorConditionToExceptionTranslator.handleError" when the IQ response is null
	@Test (expected = UnavailableProviderException.class)
	public void testSendWhenResponseIsNull() throws Exception {
		//set up
		Mockito.doReturn(null).when(this.packetSender).syncSendPacket(Mockito.any());
 		
		//exercise/verify
		this.remoteGetUserQuotaRequest.send();
	}
	
	//test case: checks if "send" is properly forwading UnauthorizedRequestException thrown by 
	//"XmppErrorConditionToExceptionTranslator.handleError" when the IQ response status is forbidden
	@Test (expected = UnauthorizedRequestException.class)
	public void testSendWhenResponseReturnsForbidden() throws Exception {
		//set up
		IQ iqResponse = new IQ();
		Mockito.doReturn(iqResponse).when(this.packetSender).syncSendPacket(Mockito.any());
		iqResponse.setError(new PacketError(PacketError.Condition.forbidden));
		
		//exercise/verify
		this.remoteGetUserQuotaRequest.send();
	}
	
	//test case: checks if "send" is properly forwading UnexpectedException thrown by 
	//"getUserQuotaFromResponse" when the user quota class name from the IQ response is undefined (wrong or not found)
	@Test(expected = UnexpectedException.class)
	public void testSendWhenImageClassIsUndefined() throws Exception {
		//set up
		IQ iqResponse = getQuotaIQResponseWithWrongClass(this.quota);
		Mockito.doReturn(iqResponse).when(this.packetSender).syncSendPacket(Mockito.any());

 		//exercise/verify
		this.remoteGetUserQuotaRequest.send();
	}
	
	private IQ getQuotaIQResponse(Quota userQuota) {
		IQ responseIq = new IQ();
        Element queryEl = responseIq.getElement().addElement(IqElement.QUERY.toString(), RemoteMethod.REMOTE_GET_USER_QUOTA.toString());
        Element instanceElement = queryEl.addElement(IqElement.USER_QUOTA.toString());

        Element instanceClassNameElement = queryEl.addElement(IqElement.USER_QUOTA_CLASS_NAME.toString());
        instanceClassNameElement.setText(userQuota.getClass().getName());

        instanceElement.setText(new Gson().toJson(userQuota));
        return responseIq;
	}
	
	private IQ getQuotaIQResponseWithWrongClass(Quota userQuota) {
		IQ responseIq = new IQ();
        Element queryEl = responseIq.getElement().addElement(IqElement.QUERY.toString(), RemoteMethod.REMOTE_GET_USER_QUOTA.toString());
        Element instanceElement = queryEl.addElement(IqElement.USER_QUOTA.toString());

        Element instanceClassNameElement = queryEl.addElement(IqElement.USER_QUOTA_CLASS_NAME.toString());
        instanceClassNameElement.setText("wrong-class-name");

        instanceElement.setText(new Gson().toJson(userQuota));
        return responseIq;
	}
}
