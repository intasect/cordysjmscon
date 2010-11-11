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
package com.cordys.coe.ac.jmsconnector;

import com.cordys.coe.ac.jmsconnector.exceptions.JMSConfigurationException;
import com.cordys.coe.ac.jmsconnector.exceptions.JMSConnectorException;
import com.cordys.coe.ac.jmsconnector.messages.LogMessages;

import com.eibus.exception.TimeoutException;

import com.eibus.soap.ApplicationTransaction;
import com.eibus.soap.BodyBlock;

import com.eibus.util.system.Native;

import com.eibus.xml.nom.Node;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;

import javax.jms.JMSException;
import javax.jms.Session;

/**
 * This class is the Implementation of ApplicationTransaction. This class will recieve the request
 * process it if it is a valid one.
 */
public class JMSConnectorTransaction
    implements ApplicationTransaction
{
    /**
     * The request type by which the request is to be redirected to different classes.
     */
    private static final String SERVICE_TYPE = "JMSCONNECTOR";
    /**
     * Holds all sessions to the destination queue managers.
     */
    private Hashtable<String, Session> destinationManagerSessions;
    /**
     * The Hashtable of request types.
     */
    private HashMap<String, String> hmSeviceTypes;
    /**
     * Holds the instance of the current JMS Connector.
     */
    private JMSConnector jmsConnector;

    /**
     * Creates the transactional object.
     *
     * @param  jmsConnector  acConfig The configuration of the application connector.
     */
    public JMSConnectorTransaction(JMSConnector jmsConnector)
    {
        this.jmsConnector = jmsConnector;

        hmSeviceTypes = new HashMap<String, String>();
        hmSeviceTypes.put(SERVICE_TYPE, SERVICE_TYPE);

        destinationManagerSessions = new Hashtable<String, Session>();

        if (JMSConnector.jmsLogger.isDebugEnabled())
        {
            JMSConnector.jmsLogger.debug("Started transaction");
        }
    }

    /**
     * This will be called when a transaction is being aborted This will rollback all the asociated
     * JMS sessions.
     */
    public void abort()
    {
        // abort all sessions
        Iterator<Session> sessions = destinationManagerSessions.values().iterator();

        while (sessions.hasNext())
        {
            Session session = sessions.next();

            try
            {
                session.rollback();
                session.close();
            }
            catch (JMSException e)
            {
            } // ignore
        }
        destinationManagerSessions.clear();

        if (JMSConnector.jmsLogger.isWarningEnabled())
        {
            JMSConnector.jmsLogger.warn(null, LogMessages.TRANSACTION_ABORT);
        }
    }

    /**
     * This method returns returns if this transaction can process requests of the given type.
     *
     * @param   sType  The type of message that needs to be processed
     *
     * @return  true if the type can be processed. Otherwise false.
     */
    public boolean canProcess(String sType)
    {
        return hmSeviceTypes.containsKey(sType);
    }

    /**
     * This method is called when the transaction is committed This will commit all the asociated
     * JMS sessions.
     */
    public void commit()
    {
        // abort all sessions
        Iterator<Session> sessions = destinationManagerSessions.values().iterator();

        while (sessions.hasNext())
        {
            Session session = sessions.next();

            try
            {
                session.commit();
                session.close();
            }
            catch (JMSException e)
            {
            } // ignore
        }
        destinationManagerSessions.clear();

        if (JMSConnector.jmsLogger.isInfoEnabled())
        {
            JMSConnector.jmsLogger.info(LogMessages.TRANSACTION_COMMIT);
        }
    }

    /**
     * Implementation of a get message method.
     *
     * @param   request         the request xml
     * @param   implementation  the xml of the method implementation
     * @param   bbResponse      the response body block
     *
     * @return  true if success
     *
     * @throws  TimeoutException       The message has not arrived witin the given time
     * @throws  JMSConnectorException  Some parameter was wrong
     */
    public boolean getMessage(int request, int implementation, BodyBlock bbResponse)
                       throws TimeoutException, JMSConnectorException
    {
        String destinationId = JMSUtil.getParameter(request, implementation, "destination", "");
        String sDestinationProviderUrl = getRequestDestinationProviderUrl(request, "destination");
        boolean waitForMessage = JMSUtil.getBooleanParameter(request, implementation,
                                                             "waitformessage", true, false);
        String correlationId = JMSUtil.getParameter(request, implementation, "correlationid", null);
        String messageSelector = JMSUtil.getParameter(request, implementation, "messageselector",
                                                      null);
        String messageFormat = JMSUtil.getParameter(request, implementation, "messageformat",
                                                    "responsemessageformat", null);
        long timeout = JMSUtil.getLongParameter(request, implementation, "timeout", false, 0L);

        if ("".equals(destinationId))
        {
            throw new JMSConnectorException("destination parameter cannot be empty!");
        }

        Destination destination = jmsConnector.getDestinationByURI(destinationId);

        if (destination == null)
        {
            throw new JMSConnectorException("destination '" + destinationId + "' not found");
        }

        boolean result = destination.getMessage(this, sDestinationProviderUrl,
                                                bbResponse.getXMLNode(), messageSelector,
                                                messageFormat, waitForMessage, correlationId,
                                                timeout);

        if (!result && waitForMessage)
        {
            throw new TimeoutException("Queue timeout while waiting for response message.");
        }

        return true;
    }

    /**
     * Returns a session for a given destination.
     *
     * @param   destination  The destination to return the session for
     *
     * @return  a session
     *
     * @throws  JMSException           An error occured while creating a JMS session
     * @throws  JMSConnectorException  The connector is not correctly configured
     */
    public Session getSessionForDestination(Destination destination)
                                     throws JMSException, JMSConnectorException
    {
        if (!destinationManagerSessions.containsKey(destination.getDestinationManager().getName()))
        {
            destinationManagerSessions.put(destination.getDestinationManager().getName(),
                                           destination.getDestinationManager().createSession());

            if (JMSConnector.jmsLogger.isDebugEnabled())
            {
                JMSConnector.jmsLogger.debug("Created new session for transaction, requested for destination access @ " +
                                             destination.getIdentifier());
            }
        }

        return (Session) destinationManagerSessions.get(destination.getDestinationManager()
                                                        .getName());
    }

    /**
     * This method processes the received request.
     *
     * @param   bbRequest   The request-bodyblock.
     * @param   bbResponse  The response-bodyblock.
     *
     * @return  true if the connector has to send the response. If someone else sends the response
     *          false is returned.
     */
    public boolean process(BodyBlock bbRequest, BodyBlock bbResponse)
    {
        boolean bReturn = true;

        try
        {
            int implementation = bbRequest.getMethodDefinition().getImplementation();
            int request = bbRequest.getXMLNode();
            int parameters = Node.getElement(implementation, "parameters");
            String action = Node.getDataElement(implementation, "action", "");

            if (JMSConnector.jmsLogger.isDebugEnabled())
            {
                JMSConnector.jmsLogger.debug("Processing request with action " + action + ": " +
                                             JMSUtil.safeFormatLogMessage(Node.writeToString(request,
                                                                                             true)));
            }

            if (action.length() == 0)
            {
                throw new JMSConnectorException("Invalid method request!");
            }

            if (action.equals("send"))
            {
                bReturn = sendMessage(request, parameters, bbResponse);
            }
            else if (action.equals("get"))
            {
                bReturn = getMessage(request, parameters, bbResponse);
            }
            else if (action.equals("request"))
            {
                bReturn = requestMessage(request, parameters, bbResponse);
            }
        }
        catch (Throwable e)
        {
            String sMessage = JMSUtil.getStackTrace(e);

            String code = "Server.Exception";
            String message = JMSUtil.findCause(e);

            JMSConnector.jmsLogger.error(e, LogMessages.TRANSACTION_ERROR, message);

            int SOAPFaultDetail = bbResponse.createSOAPFault(code, message);
            Node.setDataElement(SOAPFaultDetail, "", sMessage);

            if (bbRequest.isAsync())
            {
                bbRequest.continueTransaction();
                bReturn = false;
            }
        }
        return bReturn;
    }

    /**
     * Implementation of a request method.
     *
     * @param   request         the request xml
     * @param   implementation  the xml of the method implementation
     * @param   bbResponse      the response bodyblock
     *
     * @return  true if success
     *
     * @throws  JMSConnectorException  Some parameter was wrong
     * @throws  TimeoutException       The response has not arrived within the given time
     */
    public boolean requestMessage(int request, int implementation, BodyBlock bbResponse)
                           throws JMSConnectorException, TimeoutException
    {
        String reply2destination = JMSUtil.getParameter(request, implementation,
                                                        "reply2destination", "");
        Destination replydestination = jmsConnector.getDestinationByURI(reply2destination);

        if ("".equals(reply2destination))
        {
            throw new JMSConnectorException("reply2destination parameter cannot be empty!");
        }

        if (replydestination == null)
        {
            throw new JMSConnectorException("Reply destination '" + reply2destination +
                                            "' not found");
        }

        String messageFormat = JMSUtil.getParameter(request, implementation, "messageformat", null);
        String responseMessageFormat = JMSUtil.getParameter(request, implementation,
                                                            "responsemessageformat", null);
        boolean useCorrelationId = JMSUtil.getBooleanParameter(request, implementation,
                                                               "usecorrelation", true, true);
        long timeout = JMSUtil.getLongParameter(request, implementation, "timeout", false, 0L);

        // mpoyhone: If the response message format is not set, use the input message format.
        // Previously format used depended on the usecorrelation value.
        if ((responseMessageFormat == null) || (responseMessageFormat.length() == 0))
        {
            responseMessageFormat = messageFormat;
        }

        String[] saIdArray = sendMessage(request, implementation, useCorrelationId);

        // mpoyhone: We need to commit the session so that the message actually gets sent before
        // listening for the reply.
        {
            if (JMSConnector.jmsLogger.isDebugEnabled())
            {
                JMSConnector.jmsLogger.debug("Committing the session before listening for reply message.");
            }

            String sDestinationId = JMSUtil.getParameter(request, implementation, "destination",
                                                         "");
            Destination dDestination = jmsConnector.getDestinationByURI(sDestinationId);
            Session sRequestSession;

            if (dDestination == null)
            {
                throw new JMSConfigurationException("Unable to get the request message destination.");
            }

            sRequestSession = (Session) destinationManagerSessions.get(dDestination
                                                                       .getDestinationManager()
                                                                       .getName());

            if (sRequestSession == null)
            {
                throw new JMSConfigurationException("Unable to get the request session for destination " +
                                                    sDestinationId);
            }

            try
            {
                sRequestSession.commit();
                sRequestSession.close();
            }
            catch (JMSException e)
            {
                throw new JMSConnectorException(e,
                                                "Unable to commit the session for destination " +
                                                sDestinationId);
            }

            destinationManagerSessions.remove(dDestination.getDestinationManager().getName());
        }

        String sDestinationProviderUrl = getRequestDestinationProviderUrl(request, "destination");
        boolean result;
        String replyMessageId = null;

        if (useCorrelationId)
        {
            // Get the message with the correlation ID or the sent message ID if correlation ID is
            // not set.
            replyMessageId = ((saIdArray[1] != null) ? saIdArray[1] : saIdArray[0]);
        }

        result = replydestination.getMessage(this, sDestinationProviderUrl, bbResponse.getXMLNode(),
                                             null, responseMessageFormat, true, replyMessageId,
                                             timeout);

        if (!result)
        {
            throw new TimeoutException("Queue timeout while waiting for response message.");
        }

        if (JMSConnector.jmsLogger.isDebugEnabled())
        {
            JMSConnector.jmsLogger.debug("Response message received.");
        }

        return result;
    }

    /**
     * Implementation of a send message method.
     *
     * @param   request         the request xml
     * @param   implementation  the xml of the method implementation
     * @param   bbResponse      the response body block
     *
     * @return  true if success
     *
     * @throws  JMSConnectorException  Some parameter is wrong
     *
     * @see     sendMessage( int, int, boolean )
     */
    public boolean sendMessage(int request, int implementation, BodyBlock bbResponse)
                        throws JMSConnectorException
    {
        String[] saIdArray = sendMessage(request, implementation, true);
        Node.createTextElement("messageid", saIdArray[0], bbResponse.getXMLNode());
        return true;
    }

    /**
     * Generates a correlation ID for a message to be sent. Normally the message ID is used as a
     * correlation ID but that is available only after the message is sent, so that ID cannot be set
     * to the JMS correlation ID field inside the message. Currently this ID is a simple GUID.
     *
     * @return  Generated correlation ID.
     */
    protected String generateCorrelationId()
    {
        return Native.createGuid().replaceAll("[{}\\-]", "");
    }

    /**
     * Sends a message to a destination.
     *
     * @param   request              the xml of the request
     * @param   implementation       the xml of the method implementation
     * @param   enableCorrelationId  look for a correlation id in the request (false if called from
     *                               requestMessage)
     *
     * @return  A string array containing the message ID (index 0) and the correlation ID (index 1).
     *
     * @throws  JMSConnectorException  Some parameter is wrong
     */
    protected String[] sendMessage(int request, int implementation, boolean enableCorrelationId)
                            throws JMSConnectorException
    {
        // read parameters:
        String destinationId = JMSUtil.getParameter(request, implementation, "destination", "");
        String reply2destinationId = JMSUtil.getParameter(request, implementation,
                                                          "reply2destination", null);
        String sDestinationProviderUrl = getRequestDestinationProviderUrl(request, "destination");
        String sReplyToDestinationProviderUrl = getRequestDestinationProviderUrl(request,
                                                                                 "reply2destination");
        String correlationId = null;

        if (enableCorrelationId)
        {
            correlationId = JMSUtil.getParameter(request, implementation, "correlationid", null);

            if ((correlationId == null) || (correlationId.length() == 0))
            {
                String sCreateId = JMSUtil.getParameter(request, implementation,
                                                        "createcorrelationid", true, null);

                if ("true".equalsIgnoreCase(sCreateId))
                {
                    correlationId = generateCorrelationId();
                }
            }

            if (JMSConnector.jmsLogger.isDebugEnabled())
            {
                if (correlationId != null)
                {
                    if (JMSConnector.jmsLogger.isDebugEnabled())
                    {
                        JMSConnector.jmsLogger.debug("Using message correlation ID " +
                                                     correlationId);
                    }
                }
            }
        }

        String messageId = JMSUtil.getParameter(request, implementation, "messageid", null);
        String messageType = JMSUtil.getParameter(request, implementation, "messagetype", "text");
        int message = Node.getElement(request, "message");
        Boolean persistentDelivery = JMSUtil.getBooleanParameter(request, implementation,
                                                                 "persistentdelivery", true, null);

        long expiration = JMSUtil.getLongParameter(request, implementation, "expiration", true,
                                                   -1L);
        int priority = JMSUtil.getLongParameter(request, implementation, "priority", true, -1L)
                              .intValue();

        if ((priority < -1) || (priority > 9))
        {
            throw new JMSConnectorException("Only a priority between 0 and 9 is allowed!");
        }

        String jmsType = JMSUtil.getParameter(request, implementation, "jmstype", null);

        String messageFormat = JMSUtil.getParameter(request, implementation, "messageformat", null);

        Hashtable<String, Object> properties = new Hashtable<String, Object>();
        String overridable = Node.getAttribute(Node.getElement(implementation, "properties"),
                                               "override");

        if (!"rewrite".equals(overridable) || (Node.getElement(request, "properties") == 0))
        {
            JMSUtil.processProperties(implementation, "properties", properties, true);
        }

        if (!"none".equals(overridable))
        {
            JMSUtil.processProperties(request, "properties", properties,
                                      "change".equals(overridable));
        }

        if ("".equals(destinationId))
        {
            throw new JMSConnectorException("destination parameter cannot be empty!");
        }

        if (message == 0)
        {
            throw new JMSConnectorException("message parameter cannot be empty!");
        }

        Destination destination = jmsConnector.getDestinationByURI(destinationId);

        if (destination == null)
        {
            throw new JMSConnectorException("Destination '" + destinationId + "' not found");
        }

        Destination reply2destination = null;

        if (reply2destinationId != null)
        {
            reply2destination = jmsConnector.getDestinationByURI(reply2destinationId);

            if (reply2destination == null)
            {
                throw new JMSConnectorException("Reply destination '" + reply2destinationId +
                                                "' not found");
            }
        }

        String sMessageId = destination.sendMessage(this, message, sDestinationProviderUrl,
                                                    reply2destination,
                                                    sReplyToDestinationProviderUrl, correlationId,
                                                    persistentDelivery, expiration, jmsType,
                                                    properties, messageId, messageType,
                                                    messageFormat, priority);

        return new String[] { sMessageId, correlationId };
    }

    /**
     * Returns the provider URL attribute from 'destination' or 'reply2destination' element from the
     * SOAP request.
     *
     * @param   xRequestNode  SOAP request node.
     * @param   sElementName  XML element name.
     *
     * @return  Provider URL attribute value or <code>null</code> if it was not set.
     */
    private String getRequestDestinationProviderUrl(int xRequestNode, String sElementName)
    {
        int xNode = Node.getElement(xRequestNode, sElementName);

        if (xNode == 0)
        {
            return null;
        }

        String sValue = Node.getAttribute(xNode, Destination.DESTINATION_PHYSICALNAME_ATTRIB, "");

        return (sValue.length() > 0) ? sValue : null;
    }
}
