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
