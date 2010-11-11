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
import com.cordys.coe.ac.jmsconnector.ext.IProviderExtension;
import com.cordys.coe.ac.jmsconnector.messages.LogMessages;
import com.cordys.coe.exception.GeneralException;

import com.cordys.parser.IntegrityViolationException;
import com.cordys.parser.core.engine.ParseException;

import com.eibus.management.IManagedComponent;
import com.eibus.management.counters.CounterFactory;
import com.eibus.management.counters.IEventOccurrenceCounter;
import com.eibus.management.counters.ITimerEventValueCounter;

import com.eibus.xml.nom.Node;
import com.eibus.xml.nom.NodeType;

import java.io.UnsupportedEncodingException;

import java.nio.charset.Charset;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import javax.jms.BytesMessage;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.Session;
import javax.jms.TextMessage;

import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * This class wraps the JMS destination queue.
 *
 * @author  awisse, mpoyhone, pgussow
 */
public class Destination
{
    /**
     * Holds the list of registered destinations.
     */
    private static Hashtable<String, Destination> registeredDestinations = new Hashtable<String, Destination>();
    /**
     * Name of the dynamic destination physical name attribute for destination/reply2destination XML
     * elements.
     */
    public static final String DESTINATION_PHYSICALNAME_ATTRIB = "physical-name";
    /**
     * Indicates whether or not this is a dynamic destination.
     */
    private boolean bIsDynamic;
    /**
     * Holds the name of the binary transformation channel.
     */
    private String btcProtocol;
    /**
     * Indicates whether or not messages can be read from this queue.
     */
    private boolean canRead;
    /**
     * Indicates whether or not messages can be written to this queue.
     */
    private boolean canWrite;
    /**
     * Holds the configuration of the connector.
     */
    private JMSConnectorConfiguration config;
    /**
     * Holds the current connector.
     */
    private JMSConnector connector;
    /**
     * Default character set for this destination.
     */
    private Charset defaultCharset;
    /**
     * The actual JMS destination to write to.
     */
    private javax.jms.Destination destination;
    /**
     * The destination manager.
     */
    private DestinationManager destinationManager;
    /**
     * The destination for error messages.
     */
    private Destination errorDestination;
    /**
     * JMX Counter for the incoming messages.
     */
    private ITimerEventValueCounter incomingMessageHandlingCounter;
    /**
     * Indicates whether or not the destination is initialized properly.
     */
    private boolean initializedCorrectly = false;
    /**
     * Holds the list of triggers to execute for this destination.
     */
    private List<Trigger> lTriggers = null;
    /**
     * Holds the JMX managed component.
     */
    private IManagedComponent managedComponent;
    /**
     * JMX Counter for the number of errors.
     */
    private IEventOccurrenceCounter messageHandlingErrorCounter;
    /**
     * The name for this destination.
     */
    private String name;
    /**
     * JMX Counter for the outgoing messages.
     */
    private ITimerEventValueCounter outgoingMessageHandlingCounter;
    /**
     * The dynamic destination string.
     */
    private String sDynamicDestinationParameterString;
    /**
     * Holds the timeout for actions on the JMS queue.
     */
    private int timeout;

    /**
     * Creates a destination and registers it.
     *
     * @param   connector           DOCUMENTME
     * @param   manager             the manager of the destination
     * @param   config              the configuration
     * @param   name                the name of the destination
     * @param   parentJMXComponent  DOCUMENTME
     * @param   jndiContext         DOCUMENTME
     *
     * @throws  JMSConfigurationException  DOCUMENTME
     */
    public Destination(JMSConnector connector, DestinationManager manager,
                       JMSConnectorConfiguration config, String name,
                       IManagedComponent parentJMXComponent, InitialContext jndiContext)
                throws JMSConfigurationException
    {
        super();

        if (JMSConnector.jmsLogger.isInfoEnabled())
        {
            JMSConnector.jmsLogger.info(LogMessages.DESTINATION_INITIALIZE, name);
        }

        this.connector = connector;
        this.destinationManager = manager;
        this.config = config;
        this.name = name;
        this.timeout = destinationManager.getTimeout();

        // JMX initialization:
        managedComponent = parentJMXComponent.createSubComponent("Destination", name,
                                                                 LogMessages.JMX_DESTIANATION_SUBCOMPONENET_NAME,
                                                                 this);
        incomingMessageHandlingCounter = (ITimerEventValueCounter)
                                             managedComponent.createPerformanceCounter("handledIncomingMessageCount",
                                                                                       LogMessages.JMX_HANDLED_INCOMING_MESSAGE_COUNT,
                                                                                       CounterFactory.TIMER_EVENT_VALUE_COUNTER);
        outgoingMessageHandlingCounter = (ITimerEventValueCounter)
                                             managedComponent.createPerformanceCounter("handledOutgoingMessageCount",
                                                                                       LogMessages.JMX_HANDLED_OUTGOING_MESSAGE_COUNT,
                                                                                       CounterFactory.TIMER_EVENT_VALUE_COUNTER);
        messageHandlingErrorCounter = (IEventOccurrenceCounter)
                                          managedComponent.createPerformanceCounter("messageHandlingErrorCounter",
                                                                                    LogMessages.JMX_MESSAGE_HANDLING_ERROR_COUNTER,
                                                                                    CounterFactory.EVENT_OCCURRENCE_COUNTER);

        try
        {
            initialize(jndiContext);
        }
        catch (GeneralException e)
        {
            // fatal error, there is something wrong in the configuration xml, no setting but a
            // structural error
            throw new JMSConfigurationException(e,
                                                "Fatal error in configuration of destination " +
                                                getIdentifier());
        }
        catch (NamingException e)
        {
            // configuration error, initial context could not be found or queue could not be found
            // in the initial context
            String err = "Destination " + getIdentifier() + " not found in JNDI";

            if (!config.getRunWithConfigurationError())
            {
                throw new JMSConfigurationException(e, err);
            }
            else
            {
                JMSConnector.jmsLogger.error(e, LogMessages.DESTINATION_INITIALIZATION_JNDI_ERROR,
                                             getIdentifier());
            }
        }
        catch (JMSException e)
        {
            // configuration error, the destination is not completely correctly configured
            String err = "Error while initializing destination " + getIdentifier();

            if (!config.getRunWithConfigurationError())
            {
                throw new JMSConfigurationException(e, err);
            }
            else
            {
                JMSConnector.jmsLogger.error(e, LogMessages.DESTINATION_INITIALIZATION_ERROR,
                                             getIdentifier());
            }
        }
    }

    /**
     * Returns a destination based on its URI..
     *
     * @param   uri  the URI of the destination
     *
     * @return  the Destination
     */
    public static Destination getDestination(String uri)
    {
        return (Destination) registeredDestinations.get(uri);
    }

    /**
     * Returns if read access is specified.
     *
     * @return  true if it has read access
     */
    public boolean canRead()
    {
        return canRead;
    }

    /**
     * Returns if write access is specified.
     *
     * @return  true if it has write access
     */
    public boolean canWrite()
    {
        return canWrite;
    }

    /**
     * This method will close the JMS queue.
     *
     * @param   stopOnly  Indicates whether or not the processor is being stopped.
     *
     * @throws  JMSException  In case of any exceptions
     */
    public void close(boolean stopOnly)
               throws JMSException
    {
        if (JMSConnector.jmsLogger.isDebugEnabled())
        {
            JMSConnector.jmsLogger.debug("Closing destination: " + name + ". Stop only=" +
                                         stopOnly);
        }

        if (lTriggers != null)
        {
            for (Trigger tTrigger : lTriggers)
            {
                try
                {
                    tTrigger.close(stopOnly);
                }
                catch (Exception e)
                {
                    if (JMSConnector.jmsLogger.isDebugEnabled())
                    {
                        JMSConnector.jmsLogger.debug("Ignored exception while closing a trigger.",
                                                     e);
                    }
                }
            }

            if (!stopOnly)
            {
                lTriggers.clear();
            }
        }

        if (!bIsDynamic)
        {
            registeredDestinations.remove(JMSUtil.getDestinationURI(destination));
        }

        if (!stopOnly)
        {
            if (managedComponent != null)
            {
                try
                {
                    managedComponent.unregisterComponentTree();
                    managedComponent = null;
                }
                catch (Exception e)
                {
                    if (JMSConnector.jmsLogger.isDebugEnabled())
                    {
                        JMSConnector.jmsLogger.debug("Ignored exception while unregistering the JMX components.",
                                                     e);
                    }
                }
            }
        }

        if (JMSConnector.jmsLogger.isDebugEnabled())
        {
            JMSConnector.jmsLogger.debug("Destination closed successfully: " + name);
        }
    }

    /**
     * Creates a browser object for this queue.
     *
     * @param   transaction  the transaction to create the browser for
     *
     * @return  a QueueBrowser
     *
     * @throws  JMSConnectorException  if the destination doesn't have read access or is not a queue
     * @throws  JMSException
     */
    public QueueBrowser getBrowser(JMSConnectorTransaction transaction)
                            throws JMSConnectorException, JMSException
    {
        if (!initializedCorrectly)
        {
            throw new JMSConfigurationException("Destination is not correctly configured, see error log for details");
        }

        if (!(destination instanceof Queue))
        {
            throw new JMSConnectorException("Destination is not a queue");
        }

        if (!canRead)
        {
            throw new JMSConnectorException("Destination has no read access");
        }

        Session session = transaction.getSessionForDestination(this);

        return session.createBrowser((Queue) destination);
    }

    /**
     * Creates a consumer object for this destination.
     *
     * @param   transaction  the transaction to create the consumer for
     *
     * @return  a MessageConsumer
     *
     * @throws  JMSConnectorException  if the destination doesn't have read access
     * @throws  JMSException
     */
    public MessageConsumer getConsumer(JMSConnectorTransaction transaction)
                                throws JMSConnectorException, JMSException
    {
        if (!initializedCorrectly)
        {
            throw new JMSConfigurationException("Destination is not correctly configured, see error log for details");
        }
        return getConsumer(transaction, null);
    }

    /**
     * Creates a consumer object for this destination.
     *
     * @param   transaction  the transaction to create the consumer for
     * @param   selector     a selector for this destination
     *
     * @return  a MessageConsumer
     *
     * @throws  JMSConnectorException  if the destination doesn't have read access
     * @throws  JMSException
     */
    public MessageConsumer getConsumer(JMSConnectorTransaction transaction, String selector)
                                throws JMSConnectorException, JMSException
    {
        if (!initializedCorrectly)
        {
            throw new JMSConfigurationException("Destination is not correctly configured, see error log for details");
        }

        if (!canRead)
        {
            throw new JMSConnectorException("Destination has no read access");
        }

        Session session = transaction.getSessionForDestination(this);

        if (selector == null)
        {
            return session.createConsumer(destination);
        }

        return session.createConsumer(destination, selector);
    }

    /**
     * Returns the defaultCharset.
     *
     * @return  Returns the defaultCharset.
     */
    public Charset getDefaultCharset()
    {
        return (defaultCharset != null) ? defaultCharset : destinationManager.getDefaultCharset();
    }

    /**
     * This method returns the destination manager for this destination.
     *
     * @return  The destination manager for this destination.
     */
    public DestinationManager getDestinationManager()
    {
        return destinationManager;
    }

    /**
     * Returns the dynamicDestinationParameterString.
     *
     * @return  Returns the dynamicDestinationParameterString.
     */
    public String getDynamicDestinationParameterString()
    {
        return sDynamicDestinationParameterString;
    }

    /**
     * This method returns the indentifier for this destination which is
     * destinationmanagername.destinationname.
     *
     * @return  The indentifier for this destination which is
     *          destinationmanagername.destinationname.
     */
    public String getIdentifier()
    {
        return destinationManager.getName() + "." + name;
    }

    /**
     * This method will read messages from the current destination.
     *
     * @param   transaction              Connector transaction object/
     * @param   sDestinationProviderUrl  Provider specific URL or <code>null</code>.
     * @param   resultNode               Message is added under this node.
     * @param   selector                 Optional selector string.
     * @param   messageFormat            Message format string.
     * @param   waitForMessage           If <code>true</code> method waits for a message to be
     *                                   available.
     * @param   correlationId            Correlation ID of the message which is read or <code>
     *                                   null</code>.
     * @param   requestTimeout           Timeout value from the SOAP request or 0 if not set.
     *
     * @return  <code>true</code> if a message was read, otherwise <code>false</code>.
     *
     * @throws  JMSConnectorException
     */
    public boolean getMessage(JMSConnectorTransaction transaction, String sDestinationProviderUrl,
                              int resultNode, String selector, String messageFormat,
                              boolean waitForMessage, String correlationId, long requestTimeout)
                       throws JMSConnectorException
    {
        if (!initializedCorrectly)
        {
            throw new JMSConfigurationException("Destination is not correctly configured, see error log for details");
        }

        if (!canRead)
        {
            throw new JMSConnectorException("Destination has no read access");
        }

        if (bIsDynamic && (sDestinationProviderUrl == null))
        {
            throw new JMSConnectorException("Physical name must be specified for a dynamic destination.");
        }
        else if (!bIsDynamic && (sDestinationProviderUrl != null))
        {
            throw new JMSConnectorException("Physical name can only be specified for a dynamic destination.");
        }

        Message msg = null;
        long startTime = incomingMessageHandlingCounter.start();

        try
        {
            Session session = transaction.getSessionForDestination(this);

            MessageConsumer consumer;

            if (correlationId != null)
            {
                if (selector == null)
                {
                    selector = "JMSCorrelationID = '" + correlationId + "'";
                }
                else
                {
                    selector = "(" + selector + " ) and JMSCorrelationID = '" + correlationId + "'";
                }
            }

            javax.jms.Destination dGetDestination = destination;

            if ((sDestinationProviderUrl != null) && (sDestinationProviderUrl.length() > 0))
            {
                // Dynamic destinations are currently always queues.
                if (JMSConnector.jmsLogger.isDebugEnabled())
                {
                    JMSConnector.jmsLogger.debug("Using a dynamic get queue: " +
                                                 sDestinationProviderUrl);
                }

                dGetDestination = session.createQueue(sDestinationProviderUrl);
            }

            if (selector == null)
            {
                consumer = session.createConsumer(dGetDestination);
            }
            else
            {
                if (JMSConnector.jmsLogger.isDebugEnabled())
                {
                    JMSConnector.jmsLogger.debug("Message receive selector is: " +
                                                 JMSUtil.safeFormatLogMessage(selector));
                }

                consumer = session.createConsumer(dGetDestination, selector);
            }

            try
            {
                if (waitForMessage)
                {
                    long effectiveTimeout = (requestTimeout > 0) ? requestTimeout : timeout;

                    if (JMSConnector.jmsLogger.isDebugEnabled())
                    {
                        JMSConnector.jmsLogger.debug("Receiving a message. Timeout is " +
                                                     effectiveTimeout);
                    }

                    msg = consumer.receive(effectiveTimeout);
                }
                else
                {
                    msg = consumer.receiveNoWait();
                }

                if (msg == null)
                {
                    if (JMSConnector.jmsLogger.isDebugEnabled())
                    {
                        JMSConnector.jmsLogger.debug("No messages found from the destination.");
                    }

                    return false;
                }

                if (JMSConnector.jmsLogger.isDebugEnabled())
                {
                    JMSConnector.jmsLogger.debug("Received JMS message: " +
                                                 JMSUtil.safeFormatLogMessage(msg));
                }

                // Find out message character set.
                Charset charset = getDefaultCharset();

                if (charset == null)
                {
                    throw new JMSConnectorException("Unable to determine character set.");
                }

                String utf8String = null;
                byte[] byteMessage = null;

                if (msg instanceof BytesMessage)
                {
                    int len = (int) ((BytesMessage) msg).getBodyLength();

                    byteMessage = new byte[len];
                    ((BytesMessage) msg).readBytes(byteMessage);

                    if (!"base64".equalsIgnoreCase(messageFormat))
                    {
                        // We are not base64 encoding it, so convert it using configured character
                        // set.
                        utf8String = new String(byteMessage, charset);
                        byteMessage = null;
                    }
                }
                else if (msg instanceof TextMessage)
                {
                    TextMessage jmsTextMessage = (TextMessage) msg;
                    utf8String = jmsTextMessage.getText();
                }
                else
                {
                    throw new JMSConnectorException("Cannot handle message of type '" +
                                                    msg.getClass().getName() +
                                                    "'. Only BytesMessage and TextMessage is supported.");
                }

                // convert to xml;
                if (((messageFormat == null) || "".equals(messageFormat)) && (btcProtocol == null))
                {
                    Node.createCDataElement("message", utf8String, resultNode);
                }
                else if ("base64".equalsIgnoreCase(messageFormat))
                {
                    String result;
                    byte[] tmpBytes;

                    if (byteMessage != null)
                    {
                        tmpBytes = byteMessage;
                    }
                    else
                    {
                        try
                        {
                            tmpBytes = utf8String.getBytes("UTF-8");
                        }
                        catch (UnsupportedEncodingException e)
                        {
                            // This should not happen.
                            throw new IllegalStateException("UTF-8 encoding failed.", e);
                        }
                    }

                    result = JMSUtil.base64encode(tmpBytes);
                    Node.createCDataElement("message", result, resultNode);
                }
                else if ("xml".equalsIgnoreCase(messageFormat))
                {
                    int result = Node.getDocument(resultNode).parseString(utf8String);
                    Node.appendToChildren(result, resultNode);
                }
                else if ("xmlMessage".equalsIgnoreCase(messageFormat))
                {
                    // GS Start
                    int msgNode = Node.createElement("message", resultNode);
                    int result = Node.getDocument(resultNode).parseString(utf8String);
                    Node.appendToChildren(result, msgNode);
                    // GS End
                }
                else if ((messageFormat != null) && !"".equals(messageFormat))
                {
                    int msgNode = Node.createElement("message", resultNode);
                    Node.appendToChildren(connector.getParserEngine(messageFormat).externalToXML(utf8String
                                                                                                 .getBytes(),
                                                                                                 messageFormat,
                                                                                                 false),
                                          msgNode);
                }
                else
                {
                    int msgNode = Node.createElement("message", resultNode);
                    Node.appendToChildren(connector.getParserEngine(btcProtocol).externalToXML(utf8String
                                                                                               .getBytes(),
                                                                                               btcProtocol,
                                                                                               false),
                                          msgNode);
                }

                // If the destination is not set in the message for some reason, set it now.
                if (msg.getJMSDestination() == null)
                {
                    msg.setJMSDestination(dGetDestination);
                }

                // read default information
                JMSUtil.getInformationFromMessage(this, msg, resultNode);

                return true;
            }
            finally
            {
                consumer.close();
                incomingMessageHandlingCounter.finish(startTime);
            }
        }
        catch (Exception e)
        {
            JMSConnector.jmsLogger.error(e, LogMessages.DESTINATION_GET_MESSAGE_ERROR,
                                         getIdentifier());

            messageHandlingErrorCounter.addEvent();

            // if error destination is used...
            if ((msg != null) && (errorDestination != null))
            {
                try
                {
                    errorDestination.sendErrorMessage(msg, JMSUtil.getStackTrace(e));
                }
                catch (Exception ex)
                {
                    JMSConnector.jmsLogger.error(e,
                                                 LogMessages.DESTINATION_GET_MESSAGE_ERROR_QUEUE_ERROR,
                                                 errorDestination.getIdentifier());
                }
            }

            throw new JMSConnectorException(e);
        }
    }

    /**
     * DOCUMENT ME!
     *
     * @return  The name of the destination
     */
    public String getName()
    {
        return name;
    }

    /**
     * Returns the isDynamic.
     *
     * @return  Returns the isDynamic.
     */
    public boolean isDynamic()
    {
        return bIsDynamic;
    }

    /**
     * DOCUMENTME.
     *
     * @param   transaction                     DOCUMENTME
     * @param   messageNode                     DOCUMENTME
     * @param   sDestinationProviderUrl         DOCUMENTME
     * @param   reply2Destination               DOCUMENTME
     * @param   sReplyToDestinationProviderUrl  DOCUMENTME
     * @param   correlationID                   DOCUMENTME
     * @param   persistentDelivery              DOCUMENTME
     * @param   expiration                      DOCUMENTME
     * @param   jmsType                         DOCUMENTME
     * @param   properties                      DOCUMENTME
     * @param   messageId                       DOCUMENTME
     * @param   messageType                     DOCUMENTME
     * @param   messageFormat                   DOCUMENTME
     * @param   priority                        DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  JMSConnectorException  DOCUMENTME
     */
    public String sendMessage(JMSConnectorTransaction transaction, int messageNode,
                              String sDestinationProviderUrl, Destination reply2Destination,
                              String sReplyToDestinationProviderUrl, String correlationID,
                              Boolean persistentDelivery, long expiration, String jmsType,
                              Hashtable<String, Object> properties, String messageId,
                              String messageType, String messageFormat, int priority)
                       throws JMSConnectorException
    {
        if (!initializedCorrectly)
        {
            throw new JMSConfigurationException("Destination is not correctly configured, see error log for details");
        }

        if (!canWrite)
        {
            throw new JMSConnectorException("Destination has no write access");
        }

        if (bIsDynamic && (sDestinationProviderUrl == null))
        {
            throw new JMSConnectorException("Physical name must be specified for a dynamic destination.");
        }
        else if (!bIsDynamic && (sDestinationProviderUrl != null))
        {
            throw new JMSConnectorException("Physical name can only be specified for a dynamic destination.");
        }

        if (reply2Destination != null)
        {
            if (reply2Destination.bIsDynamic && (sReplyToDestinationProviderUrl == null))
            {
                throw new JMSConnectorException("Physical name must be specified for a dynamic reply-to-destination.");
            }
            else if (!reply2Destination.bIsDynamic && (sReplyToDestinationProviderUrl != null))
            {
                throw new JMSConnectorException("Physical name can only be specified for a dynamic reply-to-destination.");
            }
        }

        Message msg;

        long startTime = outgoingMessageHandlingCounter.start();

        try
        {
            String msgStr = null;
            byte[] msgBytes = null;

            if ("base64".equalsIgnoreCase(messageFormat) ||
                    (((messageFormat == null) || "".equals(messageFormat)) &&
                         (btcProtocol == null)))
            {
                // In C3 the whitespaces are preserved. So this check will not work anymore.
                // We need to check if there is only 1 element node as a child.
                int iCurrentChild = Node.getFirstChild(messageNode);
                int iFirstElement = 0;
                boolean bOnlyOneElement = false;

                while (iCurrentChild != 0)
                {
                    if (Node.getType(iCurrentChild) == NodeType.ELEMENT)
                    {
                        if (iFirstElement == 0)
                        {
                            iFirstElement = iCurrentChild;
                            bOnlyOneElement = true;
                        }
                        else
                        {
                            bOnlyOneElement = false;
                        }
                    }
                    iCurrentChild = Node.getNextSibling(iCurrentChild);
                }

                if (bOnlyOneElement == true)
                {
                    // Content is xml, only one child so send only that child.
                    msgStr = Node.writeToString(iFirstElement, false);
                }
                else if (Node.getNumChildren(messageNode) > 1)
                {
                    // Content is xml, more childs than one, use messageNode as parent
                    boolean completeData = true;
                    StringBuffer sbMsg = new StringBuffer();

                    for (int cnode = Node.getFirstChild(messageNode); (cnode != 0) && completeData;
                             cnode = Node.getNextSibling(cnode))
                    {
                        completeData = Node.getType(cnode) == NodeType.DATA;

                        if (completeData)
                        {
                            sbMsg.append(Node.getData(cnode));
                        }
                    }
                    msgStr = sbMsg.toString();

                    if (!completeData)
                    {
                        msgStr = Node.writeToString(messageNode, false);
                    }
                }
                else
                {
                    msgStr = Node.getData(messageNode);
                }

                if ("base64".equalsIgnoreCase(messageFormat))
                {
                    msgBytes = JMSUtil.base64decode(msgStr);
                    msgStr = null;
                }
            }
            else if (messageFormat != null)
            {
                msgStr = new String(connector.getParserEngine(messageFormat).xmlToExternal(Node.getFirstChild(messageNode),
                                                                                           messageFormat,
                                                                                           false));
            }
            else
            {
                msgStr = new String(connector.getParserEngine(btcProtocol).xmlToExternal(Node.getFirstChild(messageNode),
                                                                                         btcProtocol,
                                                                                         false));
            }

            Session session = transaction.getSessionForDestination(this);
            javax.jms.Destination dSendToDestination = destination;

            if ((sDestinationProviderUrl != null) && (sDestinationProviderUrl.length() > 0))
            {
                // Append parameters.
                sDestinationProviderUrl = JMSUtil.appendDynamicDestinationParameters(sDestinationProviderUrl,
                                                                                     this,
                                                                                     getDynamicDestinationParameterString());

                // Dynamic destinations are currently always queues.
                if (JMSConnector.jmsLogger.isDebugEnabled())
                {
                    JMSConnector.jmsLogger.debug("Using a dynamic queue: " +
                                                 sDestinationProviderUrl);
                }

                dSendToDestination = session.createQueue(sDestinationProviderUrl);
            }

            MessageProducer producer = session.createProducer(dSendToDestination);

            try
            {
                if (msgStr != null)
                {
                    msg = session.createTextMessage();
                    ((TextMessage) msg).setText(msgStr);
                }
                else if (msgBytes != null)
                {
                    msg = session.createBytesMessage();
                    ((BytesMessage) msg).writeBytes(msgBytes);
                }
                else
                {
                    throw new JMSConnectorException("No string message or byte message is set!");
                }

                if (messageId != null)
                {
                    msg.setJMSMessageID(messageId);
                }

                if (reply2Destination != null)
                {
                    try
                    {
                        javax.jms.Destination dReplyToDestination = reply2Destination.destination;

                        if ((sReplyToDestinationProviderUrl != null) &&
                                (sReplyToDestinationProviderUrl.length() > 0))
                        {
                            // Append parameters.
                            sReplyToDestinationProviderUrl = JMSUtil
                                                             .appendDynamicDestinationParameters(sReplyToDestinationProviderUrl,
                                                                                                 reply2Destination,
                                                                                                 reply2Destination
                                                                                                 .getDynamicDestinationParameterString());

                            // Dynamic destinations are currently always queues.
                            if (JMSConnector.jmsLogger.isDebugEnabled())
                            {
                                JMSConnector.jmsLogger.debug("Using a dynamic reply to queue: " +
                                                             sReplyToDestinationProviderUrl);
                            }

                            dReplyToDestination = session.createQueue(sReplyToDestinationProviderUrl);
                        }

                        msg.setJMSReplyTo(dReplyToDestination);
                    }
                    catch (JMSException e)
                    {
                        throw new JMSConnectorException(e,
                                                        "Reply destination cannot be in another (unrelated) system.");
                    }
                }

                if (correlationID != null)
                {
                    msg.setJMSCorrelationID(correlationID);
                }

                if (persistentDelivery != null)
                {
                    // please note, it is set on the producer(!) this because setting it on a
                    // message will be ignored while sending the message
                    if (persistentDelivery.booleanValue())
                    {
                        producer.setDeliveryMode(DeliveryMode.PERSISTENT);
                    }
                    else
                    {
                        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
                    }
                }

                if (expiration != -1) // also on the producer (!)
                {
                    producer.setTimeToLive(expiration);
                }

                if (priority != -1) // on the producer again (!)
                {
                    producer.setPriority(priority);
                }

                if (jmsType != null)
                {
                    msg.setJMSType(jmsType);
                }

                if (properties != null)
                {
                    Enumeration<String> pEnum = properties.keys();

                    while (pEnum.hasMoreElements())
                    {
                        String key = pEnum.nextElement();

                        // Properties starting with these prefixes are reserved, but apparently they
                        // can be set by the application. This code has been left here if we need to
                        // filter out these properties in the future.
                        /*
                         * if( key.startsWith( "JMSX" ) ) continue; if( key.startsWith( "JMS_" ) )
                         * continue; // invalid prefix, for read use only
                         */
                        Object value = properties.get(key);

                        if (JMSConnector.jmsLogger.isDebugEnabled())
                        {
                            JMSConnector.jmsLogger.debug("Setting message property " + name + "=" +
                                                         JMSUtil.safeFormatLogMessage(value));
                        }

                        if (value instanceof String)
                        {
                            msg.setStringProperty(key, (String) value);
                        }
                        else if (value instanceof Short)
                        {
                            msg.setShortProperty(key, ((Short) value).shortValue());
                        }
                        else if (value instanceof Byte)
                        {
                            msg.setByteProperty(key, ((Byte) value).byteValue());
                        }
                        else if (value instanceof Boolean)
                        {
                            msg.setBooleanProperty(key, ((Boolean) value).booleanValue());
                        }
                        else if (value instanceof Double)
                        {
                            msg.setDoubleProperty(key, ((Double) value).doubleValue());
                        }
                        else if (value instanceof Float)
                        {
                            msg.setFloatProperty(key, ((Float) value).floatValue());
                        }
                        else if (value instanceof Integer)
                        {
                            msg.setIntProperty(key, ((Integer) value).intValue());
                        }
                        else if (value instanceof Long)
                        {
                            msg.setLongProperty(key, ((Long) value).longValue());
                        }
                        else
                        {
                            msg.setObjectProperty(key, value);
                        }
                    }
                }

                if (JMSConnector.jmsLogger.isDebugEnabled())
                {
                    JMSConnector.jmsLogger.debug("Sending JMS message: " +
                                                 JMSUtil.safeFormatLogMessage(msg));
                }

                // Check if we have any extensions configured.
                List<IProviderExtension> lProvExtList = connector.getProviderExtensions();

                if (lProvExtList != null)
                {
                    for (IProviderExtension peExt : lProvExtList)
                    {
                        peExt.onBeforeSendMessage(this, msg);
                    }
                }

                producer.send(msg);

                String sMessageId = msg.getJMSMessageID();

                if (JMSConnector.jmsLogger.isDebugEnabled())
                {
                    JMSConnector.jmsLogger.debug("Message sent with ID " + sMessageId);
                }

                return sMessageId;
            }
            finally
            {
                producer.close();
                outgoingMessageHandlingCounter.finish(startTime);
            }
        }
        catch (JMSException e)
        {
            JMSConnector.jmsLogger.error(e, LogMessages.DESTINATION_SEND_ERROR_MESSAGE,
                                         getIdentifier());

            messageHandlingErrorCounter.addEvent();

            throw new JMSConnectorException(e);
        }
        catch (ParseException e)
        {
            JMSConnector.jmsLogger.error(e, LogMessages.DESTINATION_BTCPARSE_ERROR,
                                         getIdentifier());

            messageHandlingErrorCounter.addEvent();

            throw new JMSConnectorException(e);
        }
        catch (IntegrityViolationException e)
        {
            JMSConnector.jmsLogger.error(e, LogMessages.DESTINATION_BTCPARSE_ERROR,
                                         getIdentifier());

            messageHandlingErrorCounter.addEvent();

            throw new JMSConnectorException(e);
        }
    }

    /**
     * The dynamicDestinationParameterString to set.
     *
     * @param  aDynamicDestinationParameterString  The dynamicDestinationParameterString to set.
     */
    public void setDynamicDestinationParameterString(String aDynamicDestinationParameterString)
    {
        sDynamicDestinationParameterString = aDynamicDestinationParameterString;
    }

    /**
     * DOCUMENTME.
     *
     * @throws  GeneralException  DOCUMENTME
     * @throws  JMSException      DOCUMENTME
     */
    void createAnyTrigger()
                   throws GeneralException, JMSException
    {
        // create trigger session if configured
        if (canRead && config.getDestinationHasTrigger(destinationManager.getName(), name))
        {
            if (bIsDynamic)
            {
                throw new GeneralException("Dynamic destinations cannot have triggers.");
            }

            String triggerName = config.getTriggerNameForDestination(destinationManager.getName(),
                                                                     name);
            int iThreadCount = config.getTriggerThreadCountForDestination(destinationManager
                                                                          .getName(), name);

            lTriggers = new ArrayList<Trigger>(iThreadCount);

            for (int i = 0; i < iThreadCount; i++)
            {
                String sTriggerJmxId = triggerName + ((i > 0) ? ("-" + (i + 1)) : "");

                lTriggers.add(new Trigger(connector, destinationManager, config, this, triggerName,
                                          managedComponent, sTriggerJmxId));
            }
        }
    }

    /**
     * DOCUMENTME.
     *
     * @return  DOCUMENTME
     */
    Destination getErrorDestination()
    {
        if (errorDestination == null)
        {
            // Try to get the destination manager default.
            errorDestination = destinationManager.getDefaultErrorDestination();

            if (errorDestination == this)
            {
                // Error destination cannot have error destinations. Otherwise can have a loop.
                return null;
            }
        }

        return errorDestination;
    }

    /**
     * DOCUMENTME.
     *
     * @return  DOCUMENTME
     */
    javax.jms.Destination getInnerDestination()
    {
        if (bIsDynamic)
        {
            throw new IllegalStateException("Dynamic destination cannot be queried for JMS destination.");
        }

        return destination;
    }

    /**
     * For a restart, any trigger is newly created.
     *
     * @throws  GeneralException  In case of any exceptions
     * @throws  JMSException      In case of any exceptions
     */
    void restart()
          throws GeneralException, JMSException
    {
        if (lTriggers != null)
        {
            for (Trigger tTrigger : lTriggers)
            {
                tTrigger.createConsumer();
            }
        }

        if (!bIsDynamic)
        {
            // re-register it:
            registeredDestinations.put(JMSUtil.getDestinationURI(destination), this);
        }
    }

    /**
     * Sends a message to the error queue. Original message ID will be put in property
     * 'CORDYS_ORIG_MSGID' and error message in property 'CORDYS_PROCESS_ERROR'. This method creates
     * a new session.
     *
     * @param   msg           Message to be sent.
     * @param   errorMessage  Error message to be put in property 'CORDYS_PROCESS_ERROR'.
     *
     * @return  Message ID of this message in the error queue (JMS always generates a new ID).
     *
     * @throws  JMSException           Thrown if the message could not put into the error queue.
     * @throws  JMSConnectorException
     */
    String sendErrorMessage(Message msg, String errorMessage)
                     throws JMSException, JMSConnectorException
    {
        Session session = destinationManager.createTriggerSession();

        try
        {
            return sendErrorMessage(msg, errorMessage, session);
        }
        finally
        {
            if (session != null)
            {
                session.close();
            }
        }
    }

    /**
     * Sends a message to the error queue. Original message ID will be put in property
     * 'CORDYS_ORIG_MSGID' and error message in property 'CORDYS_PROCESS_ERROR'. This method uses an
     * existing session.
     *
     * @param   msg           Message to be sent.
     * @param   errorMessage  Error message to be put in property 'CORDYS_PROCESS_ERROR'.
     * @param   session       Session to be used.
     *
     * @return  Message ID of this message in the error queue (JMS always generates a new ID).
     *
     * @throws  JMSException           Thrown if the message could not put into the error queue.
     * @throws  JMSConnectorException
     */
    String sendErrorMessage(Message msg, String errorMessage, Session session)
                     throws JMSException, JMSConnectorException
    {
        Message errMsg = null;
        MessageProducer producer = null;
        String sOrigMsgId;

        try
        {
            sOrigMsgId = msg.getJMSMessageID();
        }
        catch (Exception ignored)
        {
            sOrigMsgId = "Unkwown";
        }

        try
        {
            producer = session.createProducer(destination);

            errMsg = JMSUtil.getCopyOfMessageForSession(msg, session);
            errMsg.setStringProperty("CORDYS_ORIG_MSGID", sOrigMsgId);
            errMsg.setStringProperty("CORDYS_PROCESS_ERROR", errorMessage);

            if (JMSConnector.jmsLogger.isInfoEnabled())
            {
                JMSConnector.jmsLogger.info(LogMessages.DESTINATION_SEND_ERROR_MESSAGE,
                                            errMsg.toString());
            }

            producer.send(errMsg);
            session.commit();

            return errMsg.getJMSMessageID();
        }
        finally
        {
            if (producer != null)
            {
                producer.close();
            }
        }
    }

    /**
     * DOCUMENTME.
     *
     * @throws  JMSConfigurationException  DOCUMENTME
     */
    void start()
        throws JMSConfigurationException
    {
        if (JMSConnector.jmsLogger.isInfoEnabled())
        {
            JMSConnector.jmsLogger.info(LogMessages.DESTINATION_STARTING, name);
        }

        // register error destination if used..
        String errorDestRef;

        try
        {
            errorDestRef = config.getDestinationErrorDestinationReference(destinationManager
                                                                          .getName(), name);

            if (errorDestRef != null)
            {
                errorDestination = connector.getDestinationByURI(errorDestRef);
            }
        }
        catch (GeneralException e)
        {
            // fatal error, there is something wrong in the configuration xml, no setting but a
            // structural error
            throw new JMSConfigurationException(e,
                                                "Fatal error in configuration of error destination of destination " +
                                                getIdentifier());
        }
    }

    /**
     * DOCUMENTME.
     *
     * @param   jndiContext  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     * @throws  JMSException      DOCUMENTME
     * @throws  NamingException   DOCUMENTME
     */
    private void initialize(InitialContext jndiContext)
                     throws GeneralException, JMSException, NamingException
    {
        initializedCorrectly = false;
        this.canRead = config.getDestinationHasReadAccess(destinationManager.getName(), name);
        this.canWrite = config.getDestinationHasWriteAccess(destinationManager.getName(), name);
        this.btcProtocol = config.getDestinationBTProtocol(destinationManager.getName(), name);
        this.bIsDynamic = config.isDestinationDynamic(destinationManager.getName(), name);

        if (!bIsDynamic)
        {
            destination = (javax.jms.Destination) jndiContext.lookup(config.getDestinationJNDIName(destinationManager
                                                                                                   .getName(),
                                                                                                   name));

            if (config.isDestinationDefaultErrorDestination(destinationManager.getName(), name))
            {
                if (destinationManager.getDefaultErrorDestination() != null)
                {
                    if (JMSConnector.jmsLogger.isWarningEnabled())
                    {
                        JMSConnector.jmsLogger.warn(null,
                                                    LogMessages.DESTINATION_ERROR_DESTINATION_ALREADY_SET,
                                                    destinationManager.getName(), name);
                    }
                }
                else
                {
                    destinationManager.setDefaultErrorDestination(this);
                }
            }
        }
        else
        {
            if (destinationManager.getDynamicDestination() != null)
            {
                if (JMSConnector.jmsLogger.isWarningEnabled())
                {
                    JMSConnector.jmsLogger.warn(null,
                                                LogMessages.DESTINATION_MULTIPLE_DYNAMIC_DESTINATIONS,
                                                destinationManager.getName());
                }
            }
            else
            {
                destinationManager.setDynamicDestination(this);
            }

            sDynamicDestinationParameterString = config.getDynamicDestinationParameterString(destinationManager
                                                                                             .getName(),
                                                                                             name);
        }

        if (!bIsDynamic)
        {
            // register it:
            registeredDestinations.put(JMSUtil.getDestinationURI(destination), this);
            createAnyTrigger();
        }

        String defaultCharsetName = config.getDestinationCharacterSet(destinationManager.getName(),
                                                                      name);

        if ((defaultCharsetName != null) && (defaultCharsetName.length() > 0))
        {
            defaultCharset = Charset.forName(defaultCharsetName);
        }

        initializedCorrectly = true;
    }
}
