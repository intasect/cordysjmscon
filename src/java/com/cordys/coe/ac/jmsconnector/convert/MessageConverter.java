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
package com.cordys.coe.ac.jmsconnector.convert;

import com.cordys.coe.ac.jmsconnector.exceptions.JMSConnectorException;

import com.eibus.xml.nom.Document;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

/**
 * Abstract class defines the API for JMS messages to be converted.
 *
 * @author  fvdwende
 */
public abstract class MessageConverter
{
    /**
     * DOCUMENTME.
     */
    protected Message message;
    /**
     * Values holding the JMS session and message.
     */
    protected Session session;

    /**
     * Parameterized constructor which sets the session and message.
     *
     * @param   session  The JMS session object
     * @param   message  The JMS message object
     *
     * @throws  JMSConnectorException  DOCUMENTME
     */
    public MessageConverter(Session session, Message message)
                     throws JMSConnectorException
    {
        if (session == null)
        {
            throw new JMSConnectorException("JMS session can not be left empty.");
        }

        if (message == null)
        {
            throw new JMSConnectorException("JMS message can not be left empty.");
        }
        this.session = session;
        this.message = message;
    }

    /**
     * Method defines the interface for converting JMS messages.
     *
     * @param   doc  NOM document.
     *
     * @return  A JMS TextMessage
     *
     * @throws  JMSException           In case of any exceptions
     * @throws  JMSConnectorException  In case of any exceptions
     */
    public abstract TextMessage convert(Document doc)
                                 throws JMSException, JMSConnectorException;
}
