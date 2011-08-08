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
package com.cordys.coe.ac.jmsconnector;

import com.cordys.coe.ac.jmsconnector.convert.ConvertMapMessage;
import com.cordys.coe.ac.jmsconnector.exceptions.JMSConnectorException;
import com.cordys.coe.ac.jmsconnector.messages.LogMessages;
import com.cordys.coe.exception.GeneralException;

import com.eibus.connector.nom.SOAPMessage;

import com.eibus.management.IManagedComponent;
import com.eibus.management.counters.CounterFactory;
import com.eibus.management.counters.IEventOccurrenceCounter;
import com.eibus.management.counters.ITimerEventValueCounter;

import com.eibus.soap.SOAPFault;

import com.eibus.util.Base64;
import com.eibus.util.logger.CordysLogger;

import com.eibus.xml.nom.Document;
import com.eibus.xml.nom.Node;
import com.eibus.xml.nom.XMLException;

import java.io.UnsupportedEncodingException;

import java.nio.charset.Charset;

import java.util.Enumeration;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

/**
 * This class contains the trigger definition.
 */
public class Trigger
    implements MessageListener
{
    /**
     * Holds the logger to use.
     */
    private static final CordysLogger LOG = CordysLogger.getCordysLogger(Trigger.class);
    /**
     * The name of the binary transformation channel that should be used.
     */
    private String m_btcProtocol;
    /**
     * Indicates whether or not the trigger should maintain the delivery count in order to prevent
     * duplicate handling of messages.
     */
    private boolean m_checkDeliveryCount = false;
    /**
     * The configuration of the connector.
     */
    private JMSConnectorConfiguration m_config;
    /**
     * DOCUMENTME.
     */
    private JMSConnector m_connector;
    /**
     * DOCUMENTME.
     */
    private MessageConsumer m_consumer;
    /**
     * DOCUMENTME.
     */
    private Charset m_defaultCharset;
    /**
     * DOCUMENTME.
     */
    private Destination m_destination;
    /**
     * DOCUMENTME.
     */
    private DestinationManager m_destinationManager;
    /**
     * The name of the trigger.
     */
    private String m_triggerName;
    /**
     * DOCUMENTME.
     */
    private IManagedComponent managedComponent;
    /**
     * DOCUMENTME.
     */
    private ITimerEventValueCounter messageHandlingCounter;
    /**
     * DOCUMENTME.
     */
    private IEventOccurrenceCounter messageHandlingErrorCounter;
    /**
     * The JMS message selector that should be used..
     */
    private String messageSelector = null;
    /**
     * The JMS session for this trigger.
     */
    private Session session;
    /**
     * This is used for creating Durable subscriber
     */
    private String m_subscriptionName;
    

    /**
     * Constructor which initializes this trigger. This will activate as soon as the destination
     * manager's connection will start
     *
     * @param   connector           The JMS Connector instance
     * @param   manager             The Destination manager
     * @param   config              The JMS Connector configuration
     * @param   destination         The name of the destination to listen too
     * @param   triggerName         The name of the trigger
     * @param   parentJMXComponent  The parent JMX component.
     * @param   sJmxId              The JMX id for the component.
     *
     * @throws  JMSException      In case of JMS related exceptions.
     * @throws  GeneralException  In case of configuration related exceptions.
     */
    public Trigger(JMSConnector connector, DestinationManager manager,
                   JMSConnectorConfiguration config, Destination destination, String triggerName,
                   IManagedComponent parentJMXComponent, String sJmxId)
            throws JMSException, GeneralException
    {
        super();

        if (JMSConnector.jmsLogger.isInfoEnabled())
        {
            JMSConnector.jmsLogger.info(LogMessages.TRIGGER_INITIALIZING, triggerName);
        }

        this.m_connector = connector;
        this.m_destinationManager = manager;
        this.m_triggerName = triggerName;
        this.m_config = config;
        this.m_destination = destination;
        this.m_subscriptionName = sJmxId;
        this.m_btcProtocol = config.getDestinationBTProtocol(manager.getName(),
                                                             destination.getName());
        this.messageSelector = config.getDestinationMessageSelector(manager.getName(),
                                                                    destination.getName());

        if ((this.messageSelector == null) || (this.messageSelector.trim().length() == 0))
        {
            DestinationManager.Type providerType = manager.getType();

            if (((providerType != DestinationManager.Type.JBOSSMQ) &&
                     (providerType != DestinationManager.Type.OPENJMS)) &&
                    (config.disableMessageSelector() == false))
            {
                // specifically only process messages which come here for the first time:
                this.messageSelector = "JMSXDeliveryCount = 1";
            }
            else
            {
                // onMessage needs to check the delivery count.
                m_checkDeliveryCount = true;
            }
        }

        managedComponent = parentJMXComponent.createSubComponent("Trigger", sJmxId,
                                                                 LogMessages.JMX_TRIGGER_SUBCOMPONENT_NAME,
                                                                 this);

        messageHandlingCounter = (ITimerEventValueCounter)
                                     managedComponent.createPerformanceCounter("handledMessageCount",
                                                                               LogMessages.JMX_TRIGGER_MESSAGE_HANDLING_COUNTER_NAME,
                                                                               CounterFactory.TIMER_EVENT_VALUE_COUNTER);
        messageHandlingErrorCounter = (IEventOccurrenceCounter)
                                          managedComponent.createPerformanceCounter("messageHandlingErrorCounter",
                                                                                    LogMessages.JMX_TRIGGER_MESSAGE_HANDLING_ERROR_COUNTER_NAME,
                                                                                    CounterFactory.EVENT_OCCURRENCE_COUNTER);

        try
        {
            String defaultCharsetName = config.getTriggerCharacterSet(triggerName);

            if ((defaultCharsetName != null) && (defaultCharsetName.length() > 0))
            {
                m_defaultCharset = Charset.forName(defaultCharsetName);
            }
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Unable to find out trigger character set: " +
                                            e.getMessage());
        }

        createConsumer();

        if (LOG.isDebugEnabled())
        {
            LOG.debug("Created trigger:\n" + toString());
        }
    }

    /**
     * Closes the trigger by closing the session and the consumer..
     *
     * @param   stopOnly  DOCUMENTME
     *
     * @throws  JMSException
     */
    public void close(boolean stopOnly)
               throws JMSException
    {
        if (JMSConnector.jmsLogger.isDebugEnabled())
        {
            JMSConnector.jmsLogger.debug("Closing trigger: " + m_triggerName + ". Stop only=" +
                                         stopOnly);
        }

        if (m_consumer != null)
        {
            try
            {
                m_consumer.close();
            }
            catch (JMSException e)
            {
                if (JMSConnector.jmsLogger.isDebugEnabled())
                {
                    JMSConnector.jmsLogger.debug("Ignored exception while closing a trigger consumer.",
                                                 e);
                }
            }
            m_consumer = null;
        }

        if (session != null)
        {
            try
            {
                session.close();
            }
            catch (JMSException e)
            {
                if (JMSConnector.jmsLogger.isDebugEnabled())
                {
                    JMSConnector.jmsLogger.debug("Ignored exception while closing a trigger session.",
                                                 e);
                }
            }
            session = null;
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
            JMSConnector.jmsLogger.debug("Trigger closed successfully: " + m_triggerName);
        }
    }

    /**
     * DOCUMENTME.
     *
     * @return  DOCUMENTME
     */
    public String getName()
    {
        return m_triggerName;
    }

    /* (non-Javadoc)
     * Fires as soon as a message arrives on the destination.
     * @see javax.jms.MessageListener#onMessage(javax.jms.Message)
     */
    public void onMessage(Message message)
    {
        onMessage(message, session);
    }

    /**
     * DOCUMENTME.
     *
     * @param  message     DOCUMENTME
     * @param  forSession  DOCUMENTME
     */
    public void onMessage(Message message, Session forSession)
    {
        int soapEnvelope = 0;
        int xml = 0;
        int response = 0;

        if (m_checkDeliveryCount)
        {
            int redeliveryCount = 0;

            try
            {
                redeliveryCount = message.getIntProperty("JMSXDeliveryCount");
            }
            catch (JMSException ignored)
            {
            }

            if (1 != redeliveryCount)
            {
                try
                {
                    if (JMSConnector.jmsLogger.isInfoEnabled())
                    {
                        String errorMessage = LogMessages.getFormatted(LogMessages.DO_NOT_PROCESS_REDELIVERED_MESSAGE,
                                                                       new Object[]
                                                                       {
                                                                           message.getJMSMessageID(),
                                                                           JMSUtil
                                                                           .getDestinationURI(message
                                                                                              .getJMSDestination())
                                                                       });
                        JMSConnector.jmsLogger.warn(new JMSConnectorException(errorMessage),
                                                    LogMessages.DO_NOT_PROCESS_REDELIVERED_MESSAGE,
                                                    new Object[]
                                                    {
                                                        message.getJMSMessageID(),
                                                        JMSUtil.getDestinationURI(message
                                                                                  .getJMSDestination())
                                                    });
                    }
                }
                catch (JMSException ignored)
                {
                }
                return;
            }
        }

        if (JMSConnector.jmsLogger.isDebugEnabled())
        {
            String destPhysicalName = null;
            String messageDestPhysicalName = null;

            try
            {
                if (m_destination.getInnerDestination() != null)
                {
                    destPhysicalName = JMSUtil.getDestinationURI(m_destination
                                                                 .getInnerDestination());
                }
            }
            catch (JMSException ignored)
            {
            }

            try
            {
                if (message.getJMSDestination() != null)
                {
                    messageDestPhysicalName = JMSUtil.getDestinationURI(message
                                                                        .getJMSDestination());
                }
            }
            catch (JMSException ignored)
            {
            }

            JMSConnector.jmsLogger.debug("Receiving message from destination '" +
                                         m_destination.getIdentifier() + "': " +
                                         JMSUtil.safeFormatLogMessage(message));

            JMSConnector.jmsLogger.debug("Trigger destination physical name: " + destPhysicalName);
            JMSConnector.jmsLogger.debug("From destination physical name: " +
                                         messageDestPhysicalName);
        }

        long startTime = messageHandlingCounter.start();
        String sMessageId;

        try
        {
            sMessageId = message.getJMSMessageID();
        }
        catch (Exception ignored)
        {
            sMessageId = "Unkwown";
        }

        try
        {
            // Find out message character set.
            Charset charset = m_defaultCharset;

            if (charset == null)
            {
                if (JMSConnector.jmsLogger.isDebugEnabled())
                {
                    JMSConnector.jmsLogger.debug("Trigger character set not set. Using the one from destination or destination manager.");
                }

                charset = m_destination.getDefaultCharset();

                if (charset == null)
                {
                    throw new JMSConnectorException("Unable to determine character set.");
                }
            }

            if (JMSConnector.jmsLogger.isDebugEnabled())
            {
                JMSConnector.jmsLogger.debug("Using character set: " + charset.displayName());
            }

            String utf8String;

            if (message instanceof BytesMessage)
            {
                int len = (int) ((BytesMessage) message).getBodyLength();
                byte[] utf8Text = new byte[len];
                ((BytesMessage) message).readBytes(utf8Text);
                utf8String = new String(utf8Text, charset);
            }
            else if (message instanceof TextMessage)
            {
                TextMessage jmsTextMessage = (TextMessage) message;
                utf8String = jmsTextMessage.getText();
            }
            else if (message instanceof MapMessage)
            {
                ConvertMapMessage convert = new ConvertMapMessage(forSession, message);
                utf8String = ((TextMessage) convert.convert(m_connector.getSharedNomDocument()))
                             .getText();
            }
            else
            {
                throw new JMSConnectorException("Cannot handle message of type '" +
                                                message.getClass().getName() +
                                                "'. Only BytesMessage, MapMessage " +
                                                "and TextMessage is supported.");
            }

            if (m_btcProtocol != null)
            {
                xml = m_connector.getParserEngine(m_btcProtocol).externalToXML(utf8String
                                                                               .getBytes(),
                                                                               m_btcProtocol,
                                                                               false);
            }

            JMSConnectorConfiguration.TriggerMessageInfo messageInfo = m_config
                                                                       .createMethodForInboundTrigger(m_destinationManager
                                                                                                      .getName(),
                                                                                                      m_destination
                                                                                                      .getName());

            if (messageInfo == null)
            {
                throw new JMSConnectorException("Unable to create the trigger SOAP request. Message info object is not set.");
            }

            soapEnvelope = messageInfo.soapRequestNode;
            messageInfo.soapRequestNode = 0;

            handleParameters(soapEnvelope, utf8String, xml, message);
            xml = 0;

            if (JMSConnector.jmsLogger.isDebugEnabled())
            {
                JMSConnector.jmsLogger.debug("Triggering trigger after receiving message from destination '" +
                                             m_destination.getIdentifier() + "'. Timeout: " +
                                             messageInfo.requestTimeout + ". Sending message: " +
                                             JMSUtil.safeFormatLogMessage(Node.writeToString(soapEnvelope,
                                                                                             true)));
            }

            response = m_connector.getConnector().sendAndWait(soapEnvelope,
                                                              messageInfo.requestTimeout);

            if (Node.getLocalName(Node.getFirstChild(SOAPMessage.getBodyNode(response))).equals("Fault"))
            {
                // Throw an exception which will be catched by the generic catch block. This way
                // we do not have to worry about error destinations here.
                throw new SOAPFault("",
                                    "SOAP Fault on response: \n" +
                                    JMSUtil.safeFormatLogMessage(Node.writeToString(response,
                                                                                    false)));
            }

            if (JMSConnector.jmsLogger.isDebugEnabled())
            {
                JMSConnector.jmsLogger.debug("Succesfully executed trigger after receiving message from destination '" +
                                             m_destination.getIdentifier() +
                                             "'. Received response: " +
                                             JMSUtil.safeFormatLogMessage(Node.writeToString(response,
                                                                                             true)));
            }

            forSession.commit();
        }
        catch (Exception e)
        {
            JMSConnector.jmsLogger.error(e, LogMessages.TRIGGER_RECEIVE_ERROR,
                                         m_destination.getIdentifier(), sMessageId);

            messageHandlingErrorCounter.addEvent();

            Destination dErrorDestination = m_destination.getErrorDestination();

            // if error destination is used...
            if (dErrorDestination != null)
            {
                try
                {
                    String sErrorMsgID;
                    String sErrorMessage = JMSUtil.safeFormatLogMessage(JMSUtil.getStackTrace(e));

                    // Put the message in the error destination. This will also commit this session.
                    sErrorMsgID = dErrorDestination.sendErrorMessage(message, sErrorMessage,
                                                                     forSession);

                    // Log the new message ID.
                    JMSConnector.jmsLogger.error(e,
                                                 LogMessages.TRIGGER_MESSAGE_SENT_TO_ERROR_DESTINATION,
                                                 dErrorDestination.getIdentifier(), sMessageId,
                                                 sErrorMsgID);

                    // successfully committed, so don't rollback (see below)
                    return;
                }
                catch (Exception ex)
                {
                    JMSConnector.jmsLogger.fatal(ex,
                                                 LogMessages.TRIGGER_ERROR_SENDING_TO_ERROR_DESTINATION,
                                                 dErrorDestination.getIdentifier(), sMessageId);
                }
            }

            try
            {
                // either no error destination is configured or sending to it wend wrong
                forSession.rollback();
            }
            catch (Exception ex)
            {
                JMSConnector.jmsLogger.error(ex, LogMessages.TRIGGER_ROLLBACK_ERROR,
                                             m_destination.getIdentifier(), sMessageId);
            }
        }
        finally
        {
            Node.delete(xml);
            Node.delete(soapEnvelope);
            Node.delete(Node.getRoot(response));
            messageHandlingCounter.finish(startTime);
        }
    }

    /**
     * @see  java.lang.Object#toString()
     */
    @Override public String toString()
    {
        StringBuilder returnValue = new StringBuilder(1024);

        returnValue.append(m_triggerName).append(", disabled message selector: ")
                   .append(m_config.disableMessageSelector()).append(". Message selector:");
        returnValue.append(messageSelector).append(", JMX name: ").append(managedComponent
                                                                          .getFullName());
        returnValue.append(", destination: ").append(m_destination.getName());

        return returnValue.toString();
    }

    /**
     * A method which converts the JMS message into the SOAP message based on the config.
     *
     * @param   cnode     The SOAP message parameter node pointer
     * @param   inputmsg  The JMS message body string
     * @param   xml       The JMS message body converted as XML
     * @param   message   The JMS message
     *
     * @throws  JMSException
     * @throws  UnsupportedEncodingException
     * @throws  XMLException
     */
    protected void handleParameters(int cnode, String inputmsg, int xml,
                                    Message message)
                             throws JMSException, UnsupportedEncodingException, XMLException
    {
        // walk through attributes
        int attrCount = Node.getNumAttributes(cnode);

        for (int i = 0; i < attrCount; i++)
        {
            String attrName = Node.getAttributeName(cnode, i + 1);
            String value = Node.getAttribute(cnode, attrName);

            if (value.equals("{$messageid}"))
            {
                Node.setAttribute(cnode, attrName, nullToEmpty(message.getJMSMessageID()));
            }
            else if (value.equals("{$reply2destination}"))
            {
                String[] saNames = JMSUtil.getJmsDestinationNames(m_destination,
                                                                  message.getJMSReplyTo(), true);
                String sValue = (saNames[1] != null) ? saNames[1] : saNames[0];
                Node.setAttribute(cnode, attrName, nullToEmpty(sValue));
            }
            else if (value.equals("{$fromdestination}"))
            {
                String[] saNames = JMSUtil.getJmsDestinationNames(m_destination,
                                                                  message.getJMSDestination(),
                                                                  true);
                String sValue = (saNames[1] != null) ? saNames[1] : saNames[0];
                Node.setAttribute(cnode, attrName, nullToEmpty(sValue));
            }
            else if (value.equals("{$messageprotocol}"))
            {
                Node.setAttribute(cnode, attrName, nullToEmpty(m_btcProtocol));
            }
            else if (value.equals("{$correlationid}"))
            {
                Node.setAttribute(cnode, attrName, nullToEmpty(message.getJMSCorrelationID()));
            }
            else if (value.equals("{$jmstype}"))
            {
                Node.setAttribute(cnode, attrName, nullToEmpty(message.getJMSType()));
            }
        }

        for (int child = Node.getFirstChild(cnode); child != 0; child = Node.getNextSibling(child))
        {
            handleParameters(child, inputmsg, xml, message);
        }

        String value = Node.getDataWithDefault(cnode, null);

        if (value != null)
        {
            if (value.equals("{$inputmessagebase64}"))
            {
                Node.setData(cnode, Base64.encode(inputmsg));
            }
            else if (value.equals("{$inputmessage}"))
            {
                Node.setData(cnode, nullToEmpty(inputmsg));
            }
            else if (value.equals("{$inputmessageinxml}"))
            {
                Document doc = Node.getDocument(cnode);

                Node.setData(cnode, "");

                if ((inputmsg != null) && (inputmsg.length() > 0))
                {
                    Node.appendToChildren(doc.parseString(inputmsg), Node.getParent(cnode));
                }
            }
            else if (value.equals("{$xmlmessage}"))
            {
                Node.setData(cnode, "");

                if (xml != 0)
                {
                    Node.appendToChildren(xml, Node.getParent(cnode));
                }
                else if ((inputmsg != null) && (inputmsg.length() > 0))
                {
                    Document doc = Node.getDocument(cnode);

                    Node.appendToChildren(doc.parseString(inputmsg), Node.getParent(cnode));
                }
            }
            else if (value.equals("{$messageid}"))
            {
                Node.setData(cnode, nullToEmpty(message.getJMSMessageID()));
            }
            else if (value.equals("{$reply2destination}"))
            {
                if (message.getJMSReplyTo() != null)
                {
                    String[] saNames = JMSUtil.getJmsDestinationNames(m_destination,
                                                                      message.getJMSReplyTo(),
                                                                      true);

                    Node.setData(cnode, saNames[0]);

                    if (saNames[1] != null)
                    {
                        int xParent = Node.getParent(cnode);

                        if (xParent != 0)
                        {
                            Node.setAttribute(xParent, Destination.DESTINATION_PHYSICALNAME_ATTRIB,
                                              saNames[1]);
                        }
                    }
                }
                else
                {
                    String reply2queue = JMSUtil.getDestinationIdentifier(JMSUtil.getDestinationURI(message
                                                                                                    .getJMSReplyTo()));
                    Node.setData(cnode, nullToEmpty(reply2queue));
                }
            }
            else if (value.equals("{$fromdestination}"))
            {
                String[] saNames = JMSUtil.getJmsDestinationNames(m_destination,
                                                                  message.getJMSDestination(),
                                                                  true);

                Node.setData(cnode, saNames[0]);

                if (saNames[1] != null)
                {
                    int xParent = Node.getParent(cnode);

                    if (xParent != 0)
                    {
                        Node.setAttribute(xParent, Destination.DESTINATION_PHYSICALNAME_ATTRIB,
                                          saNames[1]);
                    }
                }
            }
            else if (value.equals("{$messageprotocol}"))
            {
                Node.setData(cnode, nullToEmpty(m_btcProtocol));
            }
            else if (value.equals("{$correlationid}"))
            {
                Node.setData(cnode, nullToEmpty(message.getJMSCorrelationID()));
            }
            else if (value.equals("{$jmstype}"))
            {
                Node.setData(cnode, nullToEmpty(message.getJMSType()));
            }
            else if (value.equals("{$properties}"))
            {
                Node.setData(cnode, ""); // empty it

                Enumeration<?> properties = message.getPropertyNames();

                while (properties.hasMoreElements())
                {
                    String key = (String) properties.nextElement();

                    Object objValue = message.getObjectProperty(key);
                    int nnode = Node.createTextElement("property", String.valueOf(objValue),
                                                       Node.getParent(cnode));
                    Node.setAttribute(nnode, "name", key);

                    if (objValue instanceof String)
                    {
                        Node.setAttribute(nnode, "type", "String");
                    }
                    else if (objValue instanceof Short)
                    {
                        Node.setAttribute(nnode, "type", "Short");
                    }
                    else if (objValue instanceof Byte)
                    {
                        Node.setAttribute(nnode, "type", "Byte");
                    }
                    else if (objValue instanceof Boolean)
                    {
                        Node.setAttribute(nnode, "type", "Boolean");
                    }
                    else if (objValue instanceof Double)
                    {
                        Node.setAttribute(nnode, "type", "Double");
                    }
                    else if (objValue instanceof Float)
                    {
                        Node.setAttribute(nnode, "type", "Float");
                    }
                    else if (objValue instanceof Integer)
                    {
                        Node.setAttribute(nnode, "type", "Integer");
                    }
                    else if (objValue instanceof Long)
                    {
                        Node.setAttribute(nnode, "type", "Long");
                    }
                    else
                    {
                        Node.setAttribute(nnode, "type", "Object");
                    }
                }
            }
        }
    }

    /**
     * Creates the JMS consumer that listens for incoming messages.
     *
     * @throws  GeneralException  In case of any exceptions
     * @throws  JMSException      In case of any exceptions
     */
    void createConsumer()
                 throws GeneralException, JMSException
    {
    	boolean created = false;        
        try
		{        	
	    	if ((session != null) || (m_consumer != null))
	        {
	            throw new GeneralException("Trigger is already initialized!");
	        }
	    	
	        session = m_destinationManager.createTriggerSession();
	
	        if (JMSConnector.jmsLogger.isDebugEnabled())
	        {
	            JMSConnector.jmsLogger.debug("Creating message consumer with specific message selector: " +
	                                         this.messageSelector);
	        }	        
	
	        // Durable subscription can be created for Topic only. The subscriber name is set to Trigger name
	        if (m_destination.isDurableSubscriber())
	        {
				if (JMSConnector.jmsLogger.isDebugEnabled())
				{
				   JMSConnector.jmsLogger.debug("Creating Durable subscriber with Subscription Name :"+this.m_subscriptionName);
				}
				if (m_destination.getInnerDestination() instanceof Topic)
				{
					m_consumer = session.createDurableSubscriber((Topic)m_destination.getInnerDestination(),this.m_subscriptionName,
															this.messageSelector,false);
				}
				else
				{
					throw new GeneralException(this.m_destination.getName() + " is not a Topic. Durable subscriber can be created for Topic only.");
				}
	        }
	        else
	        {        
	        	m_consumer = session.createConsumer(m_destination.getInnerDestination(),
	                                            this.messageSelector);
	        }
	        m_consumer.setMessageListener(this);
	        created = true;	        
		}catch (JMSException e)
		{
			throw e;
		}catch (Exception e)
		{	
			throw new GeneralException(e);
		}
		finally
		{
			// Shutdown the Trigger 
			if (!created) 
			{
				close(false);
			}
		}

    }

    /**
     * DOCUMENTME.
     *
     * @return  DOCUMENTME
     */
    Destination getDestination()
    {
        return m_destination;
    }

    /**
     * DOCUMENTME.
     *
     * @return  DOCUMENTME
     */
    DestinationManager getDestinationManager()
    {
        return m_destinationManager;
    }

    /**
     * Fixes null string references with an empty string.
     *
     * @param   sStr  The string.
     *
     * @return  The given string or if it was <code>null</code>, an empty string.
     */
    private static String nullToEmpty(String sStr)
    {
        return (sStr != null) ? sStr : "";
    }
}
