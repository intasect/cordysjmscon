/**
 * (c) 2006 Cordys R&D B.V. All rights reserved. The computer program(s) is the
 * proprietary information of Cordys B.V. and provided under the relevant
 * License Agreement containing restrictions on use and disclosure. Use is
 * subject to the License Agreement.
 */
package com.cordys.coe.ac.jmsconnector.ext;

import javax.jms.Message;

import com.cordys.coe.ac.jmsconnector.Destination;
import com.cordys.coe.ac.jmsconnector.JMSConnector;

/**
 * An interface for extending provider the connector to support
 * provider specific extensions.
 *
 * @author mpoyhone
 */
public interface IProviderExtension
{
    /**
     * Called when the extension is being initialize.
     * @param jcConnector JMSConnector has the holds the extension.
     * @return <code>true</code> if this extension should be enabled.
     */
    boolean initialize(JMSConnector jcConnector);

    /**
     * Called before a message is being sent.
     * @param dDest JMS connector destination to which the message is being sent.
     * @param mMsg Message to be sent.
     */
    void onBeforeSendMessage(Destination dDest, Message mMsg);
}
