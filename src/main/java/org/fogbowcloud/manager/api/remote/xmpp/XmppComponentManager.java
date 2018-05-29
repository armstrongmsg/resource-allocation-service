package org.fogbowcloud.manager.api.remote.xmpp;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.api.remote.xmpp.handlers.CreateRemoteOrderHandler;
import org.fogbowcloud.manager.api.remote.xmpp.handlers.GetRemoteComputeHandler;
import org.jamppa.component.XMPPComponent;

public class XmppComponentManager extends XMPPComponent {

    private static Logger LOGGER = Logger.getLogger(XmppComponentManager.class);

    public XmppComponentManager(String jid, String password, String server, int port, long timeout) {
        super(jid, password, server, port, timeout);
        // instantiate all handlers here
        addSetHandler(new CreateRemoteOrderHandler());
        addGetHandler(new GetRemoteComputeHandler());
    }

}
