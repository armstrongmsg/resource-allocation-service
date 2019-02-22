package cloud.fogbow.ras.core.intercomponent.xmpp.requesters;

import cloud.fogbow.common.models.FederationUser;
import cloud.fogbow.ras.core.intercomponent.xmpp.IqElement;
import cloud.fogbow.ras.core.intercomponent.xmpp.PacketSenderHolder;
import cloud.fogbow.ras.core.intercomponent.xmpp.RemoteMethod;
import cloud.fogbow.ras.core.intercomponent.xmpp.XmppErrorConditionToExceptionTranslator;
import com.google.gson.Gson;
import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.xmpp.packet.IQ;

public class RemoteDeleteSecurityRuleRequest implements RemoteRequest<Void> {

    private static final Logger LOGGER = Logger.getLogger(RemoteDeleteSecurityRuleRequest.class);

    private String provider;
    private String cloudName;
    private String ruleId;
    private FederationUser federationUser;

    public RemoteDeleteSecurityRuleRequest(String provider, String cloudName, String ruleId, FederationUser federationUser) {
        this.provider = provider;
        this.cloudName = cloudName;
        this.ruleId = ruleId;
        this.federationUser = federationUser;
    }

    @Override
    public Void send() throws Exception {
        IQ iq = marshal();

        IQ response = (IQ) PacketSenderHolder.getPacketSender().syncSendPacket(iq);
        XmppErrorConditionToExceptionTranslator.handleError(response, this.provider);
        return null;
    }

    private IQ marshal() {
        IQ iq = new IQ(IQ.Type.set);
        iq.setTo(provider);

        Element queryElement = iq.getElement().addElement(IqElement.QUERY.toString(),
                RemoteMethod.REMOTE_DELETE_SECURITY_RULE.toString());

        Element cloudNameElement = queryElement.addElement(IqElement.CLOUD_NAME.toString());
        cloudNameElement.setText(cloudName);

        Element userElement = queryElement.addElement(IqElement.FEDERATION_USER.toString());
        userElement.setText(new Gson().toJson(federationUser));

        Element ruleIdElement = queryElement.addElement(IqElement.RULE_ID.toString());
        ruleIdElement.setText(ruleId);

        return iq;
    }
}