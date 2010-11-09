/**
 * © 2004 Cordys R&D B.V. All rights reserved.
 * The computer program(s) is the proprietary information of Cordys R&D B.V.
 * and provided under the relevant License Agreement containing restrictions
 * on use and disclosure. Use is subject to the License Agreement.
 */
package com.cordys.coe.ac.jmsconnector.exceptions;

import com.cordys.coe.exception.GeneralException;

/**
 * General Exception class for the JMSConnector.
 */
public class JMSConnectorException extends GeneralException
{
    /**
     * DOCUMENTME.
     */
    private static final long serialVersionUID = -8270755089090362513L;

    /**
     * Creates a new instance of <code>JMSConnectorException</code> without a cause.
     */
    public JMSConnectorException()
    {
    }

    /**
     * Creates a new instance of <code>JMSConnectorException</code> based on the the throwable.
     *
     * @param  tThrowable  The source throwable.
     */
    public JMSConnectorException(Throwable tThrowable)
    {
        super(tThrowable);
    }

    /**
     * Constructs an instance of <code>TranslatorException</code> with the specified detail message.
     *
     * @param  sMessage  the detail message.
     */
    public JMSConnectorException(String sMessage)
    {
        super(sMessage);
    }

    /**
     * Creates a new instance of <code>JMSConnectorException</code> based on the the throwable.
     *
     * @param  tThrowable  The cause.
     * @param  sMessage    The additional message.
     */
    public JMSConnectorException(Throwable tThrowable, String sMessage)
    {
        super(tThrowable, sMessage);
    }
}
