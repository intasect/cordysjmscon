/*
 *  Copyright 2007 Cordys R&D B.V. 
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
