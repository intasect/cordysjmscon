/*
 *  Copyright © 2005 Cordys Systems B.V. All rights reserved.
 *
 * The computer program(s) is the proprietary information of Cordys Systems B.V., and provided under
 * the relevant Agreement between yourself and Cordys Systems B.V. containing restrictions on use
 * and disclosure, and are also protected by copyright, patent, and other intellectual and
 * industrial property laws. No part of this program may be used/copied without the prior written
 * consent of Cordys Systems B.V.
 *
 * Project     : JMS Connector File Name   : JMSConfigurationException.java Date        : Jan 31, 2006
 * Author      : awisse
 */
package com.cordys.coe.ac.jmsconnector.exceptions;

/**
 * DOCUMENTME.
 *
 * @author  $author$
 */
public class JMSConfigurationException extends JMSConnectorException
{
    /**
     * DOCUMENTME.
     */
    private static final long serialVersionUID = 1358560764606753394L;

    /**
     * Creates a new instance of <code>JMSConfigurationException</code> without a cause.
     */
    public JMSConfigurationException()
    {
    }

    /**
     * Creates a new instance of <code>JMSConfigurationException</code> based on the the throwable.
     *
     * @param  tThrowable  The source throwable.
     */
    public JMSConfigurationException(Throwable tThrowable)
    {
        super(tThrowable);
    }

    /**
     * Creates a new JMSConfigurationException object.
     *
     * @param  sMessage  DOCUMENTME
     */
    public JMSConfigurationException(String sMessage)
    {
        super(sMessage);
    }

    /**
     * Creates a new instance of <code>JMSConfigurationException</code> based on the the throwable.
     *
     * @param  tThrowable  The cause.
     * @param  sMessage    The additional message.
     */
    public JMSConfigurationException(Throwable tThrowable, String sMessage)
    {
        super(tThrowable, sMessage);
    }
}
