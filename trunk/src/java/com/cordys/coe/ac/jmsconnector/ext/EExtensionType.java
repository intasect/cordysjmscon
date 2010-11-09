/**
 * (c) 2006 Cordys R&D B.V. All rights reserved. The computer program(s) is the
 * proprietary information of Cordys B.V. and provided under the relevant
 * License Agreement containing restrictions on use and disclosure. Use is
 * subject to the License Agreement.
 */
package com.cordys.coe.ac.jmsconnector.ext;

/**
 * A static class for registering the provider specific extensions.
 *
 * @author  mpoyhone
 */
public enum EExtensionType
{
    /**
     * WebSphere MQ extension name for setting the message ID when sending a message to the queue.
     * This functionality is not allowed by the JMS standard.
     */
    WSMQ_SETMESSAGEID("com.cordys.coe.ac.jmsconnector.ext.webspheremq.MQSetOutgoingMessageId");

    /**
     * Implementing class.
     */
    private String sClassName;

    /**
     * Constructor for EExtensionType.
     *
     * @param  sClassName  Implementing class.
     */
    private EExtensionType(String sClassName)
    {
        this.sClassName = sClassName;
    }

    /**
     * Returns the implementation class.
     *
     * @return  The implementation class.
     */
    public String getClassName()
    {
        return sClassName;
    }
}
