package org.fogbowcloud.ras.core.intercomponent.xmpp.requesters;

import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.fogbowcloud.ras.core.intercomponent.RemoteFacade;
import org.fogbowcloud.ras.core.intercomponent.xmpp.IqElement;
import org.fogbowcloud.ras.core.intercomponent.xmpp.RemoteMethod;
import org.fogbowcloud.ras.core.intercomponent.xmpp.XmppExceptionToErrorConditionTranslator;
import org.fogbowcloud.ras.core.models.securitygroups.SecurityGroupRule;
import org.fogbowcloud.ras.util.GsonHolder;
import org.jamppa.component.handler.AbstractQueryHandler;
import org.xmpp.packet.IQ;

public class RemoteCreateSecurityRuleRequestHandler extends AbstractQueryHandler {
    public static final Logger LOGGER = Logger.getLogger(RemoteCreateSecurityRuleRequestHandler.class);

    public RemoteCreateSecurityRuleRequestHandler() {
        super(RemoteMethod.REMOTE_CREATE_SECURITY_GROUP.toString());
    }

    @Override
    public IQ handle(IQ iq) {
        String orderId = unmarshalOrderId(iq);
        SecurityGroupRule securityGroupRule = unmarshalSecurityRule(iq);

        IQ response = IQ.createResultIQ(iq);
        try {
            RemoteFacade.getInstance().createSecurityRule(iq.getFrom().toBareJID(), orderId, securityGroupRule);
        } catch (Throwable e) {
            XmppExceptionToErrorConditionTranslator.updateErrorCondition(response, e);
        }

        return response;
    }

    private SecurityGroupRule unmarshalSecurityRule(IQ iq) {
        Element queryElement = iq.getElement().element(IqElement.QUERY.toString());
        Element securityRuleElement = queryElement.element(IqElement.SECURITY_RULE.toString());
        return GsonHolder.getInstance()
                .fromJson(securityRuleElement.getText(), SecurityGroupRule.class);
    }

    private String unmarshalOrderId(IQ iq) {
        Element queryElement = iq.getElement().element(IqElement.QUERY.toString());
        Element orderIdElement = queryElement.element(IqElement.ORDER_ID.toString());
        return orderIdElement.getText();
    }

}
