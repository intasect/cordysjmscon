/*
 *
 *  Copyright 2004 Cordys R&D B.V. 
 *
 *  This file is part of the Cordys JMS Connector. 
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
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
