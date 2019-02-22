package cloud.fogbow.ras.core.intercomponent.xmpp.requesters;

import cloud.fogbow.common.exceptions.UnexpectedException;
import cloud.fogbow.common.models.FederationUser;
import cloud.fogbow.ras.core.intercomponent.xmpp.IqElement;
import cloud.fogbow.ras.core.intercomponent.xmpp.PacketSenderHolder;
import cloud.fogbow.ras.core.intercomponent.xmpp.RemoteMethod;
import cloud.fogbow.ras.core.intercomponent.xmpp.XmppErrorConditionToExceptionTranslator;
import cloud.fogbow.ras.core.models.ResourceType;
import cloud.fogbow.ras.core.models.quotas.Quota;
import com.google.gson.Gson;
import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.xmpp.packet.IQ;

public class RemoteGetUserQuotaRequest implements RemoteRequest<Quota> {
    private static final Logger LOGGER = Logger.getLogger(RemoteGetUserQuotaRequest.class);

    private String provider;
    private String cloudName;
    private FederationUser federationUser;
    private ResourceType resourceType;

    public RemoteGetUserQuotaRequest(String provider, String cloudName, FederationUser federationUser,
                                     ResourceType resourceType) {
        this.provider = provider;
        this.cloudName = cloudName;
        this.federationUser = federationUser;
        this.resourceType = resourceType;
    }

    @Override
    public Quota send() throws Exception {
        IQ iq = marshal(this.provider, this.cloudName, this.federationUser, this.resourceType);
        IQ response = (IQ) PacketSenderHolder.getPacketSender().syncSendPacket(iq);

        XmppErrorConditionToExceptionTranslator.handleError(response, this.provider);
        Quota quota = unmarshalUserQuota(response);
        return quota;
    }

    public static IQ marshal(String provider, String cloudName, FederationUser federationUser, ResourceType resourceType) {
        IQ iq = new IQ(IQ.Type.get);
        iq.setTo(provider);

        Element queryElement = iq.getElement().addElement(IqElement.QUERY.toString(),
                RemoteMethod.REMOTE_GET_USER_QUOTA.toString());

        Element cloudNameElement = queryElement.addElement(IqElement.CLOUD_NAME.toString());
        cloudNameElement.setText(cloudName);

        Element userElement = queryElement.addElement(IqElement.FEDERATION_USER.toString());
        userElement.setText(new Gson().toJson(federationUser));

        Element orderTypeElement = queryElement.addElement(IqElement.INSTANCE_TYPE.toString());
        orderTypeElement.setText(resourceType.toString());

        return iq;
    }

    private Quota unmarshalUserQuota(IQ response) throws UnexpectedException {
        Element queryElement = response.getElement().element(IqElement.QUERY.toString());
        String quotaStr = queryElement.element(IqElement.USER_QUOTA.toString()).getText();

        String instanceClassName = queryElement.element(IqElement.USER_QUOTA_CLASS_NAME.toString()).getText();

        Quota quota = null;
        try {
            quota = (Quota) new Gson().fromJson(quotaStr, Class.forName(instanceClassName));
        } catch (Exception e) {
            throw new UnexpectedException(e.getMessage());
        }

        return quota;
    }
}