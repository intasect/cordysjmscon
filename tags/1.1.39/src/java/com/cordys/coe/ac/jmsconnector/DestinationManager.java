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

import com.cordys.coe.ac.jmsconnector.exceptions.JMSConfigurationException;
import com.cordys.coe.ac.jmsconnector.exceptions.JMSConnectorException;
import com.cordys.coe.ac.jmsconnector.messages.LogMessages;
import com.cordys.coe.exception.GeneralException;

import com.eibus.localization.StringFormatter;

import com.eibus.management.IManagedComponent;

import com.eibus.util.logger.Severity;

import java.nio.charset.Charset;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TemporaryQueue;

import javax.naming.CommunicationException;
import javax.naming.InitialContext;
import javax.naming.NamingException;

/**
 * DOCUMENTME.
 *
 * @author  $author$
 */
public class DestinationManager
{
    /**
     * DOCUMENTME.
     */
    private static final String SHUTDOWN_TEMP_QUEUE_NAME = "...tempqueue";
    /**
     * DOCUMENTME.
     */
    private static final String DEFAULT_CHARSET_NAME = "UTF-8";
    /**
     * DOCUMENTME.
     */
    private boolean bCheckConnectionOnRequest;
    /**
     * DOCUMENTME.
     */
    private boolean bCreateShutdownTempQueue;
    /**
     * DOCUMENTME.
     */
    private boolean bShutdownRequested;
    /**
     * DOCUMENTME.
     */
    private ConnectionFactory cFactory;
    /**
     * DOCUMENTME.
     */
    private Connection connection;
    /**
     * DOCUMENTME.
     */
    private Destination dDefaultErrorDestination;
    /**
     * DOCUMENTME.
     */
    private Destination dDynamicDestination;
    /**
     * DOCUMENTME.
     */
    private final Charset defaultCharset;
    /**
     * DOCUMENTME.
     */
    private Hashtable<String, Destination> destinations;
    /**
     * DOCUMENTME.
     */
    private Destination dShutdownDestination;
    /**
     * DOCUMENTME.
     */
    private boolean initializedCorrectly = false;
    /**
     * DOCUMENTME.
     */
    private JMSConnector jcAppConnector;
    /**
     * DOCUMENTME.
     */
    private IManagedComponent managedComponent;
    /**
     * DOCUMENTME.
     */
    private MessageConsumer mcShutdownMessageConsumer;
    /**
     * DOCUMENTME.
     */
    private String name;
    /**
     * DOCUMENTME.
     */
    private String sConnectionPassword;
    /**
     * DOCUMENTME.
     */
    private String sConnectionUsername;
    /**
     * DOCUMENTME.
     */
    private Session sShutdownListenSession;
    /**
     * DOCUMENTME.
     */
    private int timeout;
    /**
     * DOCUMENTME.
     */
    private TemporaryQueue tqShutdownListenQueue;
    /**
     * DOCUMENTME.
     */
    private Type type;

    /**
     * Creates a new DestinationManager object.
     *
     * @param   connector           DOCUMENTME
     * @param   config              DOCUMENTME
     * @param   managerName         DOCUMENTME
     * @param   parentJMXComponent  DOCUMENTME
     *
     * @throws  JMSConfigurationException  DOCUMENTME
     */
    public DestinationManager(JMSConnector connector, JMSConnectorConfiguration config,
                              String managerName, IManagedComponent parentJMXComponent)
                       throws JMSConfigurationException
    {
        super();

        if (JMSConnector.jmsLogger.isInfoEnabled())
        {
            JMSConnector.jmsLogger.info(LogMessages.DESTINATIONMANAGER_INITIALIZING, managerName);
        }

        this.name = managerName;
        this.destinations = new Hashtable<String, Destination>();
        this.jcAppConnector = connector;

        try
        {
            sConnectionUsername = config.getDestinationManagerConnectionUser(name);

            if ((sConnectionUsername != null) && (sConnectionUsername.length() > 0))
            {
                sConnectionPassword = config.getDestinationManagerConnectionPassword(name);

                if (sConnectionPassword == null)
                {
                    sConnectionPassword = "";
                }
            }

            String defaultCharsetName = config.getDestinationManagerCharacterSet(managerName);

            if ((defaultCharsetName != null) && (defaultCharsetName.length() > 0))
            {
                defaultCharset = Charset.forName(defaultCharsetName);
            }
            else
            {
                defaultCharset = Charset.forName(DEFAULT_CHARSET_NAME);
            }
        }
        catch (GeneralException e)
        {
            // fatal error, there is something wrong in the configuration xml, no setting but a
            // structural error
            throw new JMSConfigurationException(e,
                                                "Fatal error in configuration of destination manager " +
                                                name);
        }

        boolean bInitOk = false;

        try
        {
            initialize(config, parentJMXComponent);
            bInitOk = true;
        }
        finally
        {
            if (!bInitOk)
            {
                // Clean up.
                try
                {
                    boolean stopOnly = false;
                    close(stopOnly);
                }
                catch (JMSException ignored)
                {
                }
            }
        }
    }

    /**
     * In case JMS connection was lost, try to restart.
     */
    public void checkConnection()
    {
        if (connection == null)
        {
            try
            {
                if (JMSConnector.jmsLogger.isInfoEnabled())
                {
                    JMSConnector.jmsLogger.info(LogMessages.DESTINATIONMANAGER_TRYING_TO_RESTART,
                                                name);
                }

                restart();
            }
            catch (Exception e)
            {
                if (JMSConnector.jmsLogger.isInfoEnabled())
                {
                    JMSConnector.jmsLogger.info(LogMessages.DESTINATIONMANAGER_UNABLE_TO_RESTART,
                                                name, e.getMessage());
                }

                boolean stopOnly = true;

                try
                {
                    close(stopOnly);
                }
                catch (Exception e2)
                {
                    // ignore
                }
            }
        }
        else
        {
        	// This is a fix for configuring JMS Connector in failover mode. 
        	// JMS Connector needs to be configured in failover mode if the destinations or topics
        	
        	// restart all destinations
            Enumeration<com.cordys.coe.ac.jmsconnector.Destination> enumDestinations = destinations.elements();
            while (enumDestinations.hasMoreElements())
            {
            	Destination destination = enumDestinations.nextElement();
            	if (!destination.isInitializedCorrectly())
            	{
            		try 
            		{
            			destination.restart();
					}catch (Exception e) 
					{
						JMSConnector.jmsLogger.warn("Unable to restart the destination "+destination.getName(),e);												
					}
            	}
            }
        	 
        }
    }

    /**
     * stopOnly: - false: close the destination manager incl clearing destinations - true: stop
     * only. Keep destinations resident as a restart might happen
     *
     * @param   stopOnly  DOCUMENTME
     *
     * @throws  JMSException  In case of any exceptions
     */
    public void close(boolean stopOnly)
               throws JMSException
    {
        close(stopOnly, false);
    }

    /**
     * DOCUMENTME.
     *
     * @param  stopOnly           DOCUMENTME
     * @param  connectorStopping  DOCUMENTME
     */
    public void close(boolean stopOnly, boolean connectorStopping)
    {
        if (JMSConnector.jmsLogger.isDebugEnabled())
        {
            JMSConnector.jmsLogger.debug("Closing destination manager: " + name + ". Stop only=" +
                                         stopOnly + ", connector stopping=" + connectorStopping);
        }

/*        if (connectorStopping) {
 *          if (JMSConnector.jmsLogger.isDebugEnabled())         {
 * JMSConnector.jmsLogger.debug("Connector is stopping, so just terminating the connection.");  }
 *              if (connection != null) {             if (JMSConnector.jmsLogger.isDebugEnabled())
 *           { JMSConnector.jmsLogger.debug("Closing JMS connection for destination manager: " +
 * name);   }                             try             {               connection.close(); }
 *        catch (Throwable e)             { if (JMSConnector.jmsLogger.isDebugEnabled())
 *     { JMSConnector.jmsLogger.debug("Ignored exception while closing the the connection.", e);
 *       }             }         }                    return;     }*/

        tqShutdownListenQueue = null;

        if (mcShutdownMessageConsumer != null)
        {
            if (JMSConnector.jmsLogger.isDebugEnabled())
            {
                JMSConnector.jmsLogger.debug("Closing shutdown message consumer for destination manager: " +
                                             name);
            }

            try
            {
                mcShutdownMessageConsumer.close();
            }
            catch (Exception e)
            {
                if (JMSConnector.jmsLogger.isDebugEnabled())
                {
                    JMSConnector.jmsLogger.debug("Ignored exception while closing the shutdown message consumer.",
                                                 e);
                }
            }

            mcShutdownMessageConsumer = null;
        }

        if (sShutdownListenSession != null)
        {
            if (JMSConnector.jmsLogger.isDebugEnabled())
            {
                JMSConnector.jmsLogger.debug("Closing shutdown listen session for destination manager: " +
                                             name);
            }

            try
            {
                sShutdownListenSession.close();
            }
            catch (Exception e)
            {
                if (JMSConnector.jmsLogger.isDebugEnabled())
                {
                    JMSConnector.jmsLogger.debug("Ignored exception while closing the shutdown session.",
                                                 e);
                }
            }

            sShutdownListenSession = null;
        }

        if ((!stopOnly) && (managedComponent != null))
        {
            if (JMSConnector.jmsLogger.isDebugEnabled())
            {
                JMSConnector.jmsLogger.debug("Unregistering JMX components for destination manager: " +
                                             name);
            }

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

        if (destinations != null)
        {
            // stop the triggers:
            Enumeration<com.cordys.coe.ac.jmsconnector.Destination> enumDestinations = destinations
                                                                                       .elements();

            while (enumDestinations.hasMoreElements())
            {
                try
                {
                    enumDestinations.nextElement().close(stopOnly);
                }
                catch (Exception e)
                {
                    if (JMSConnector.jmsLogger.isDebugEnabled())
                    {
                        JMSConnector.jmsLogger.debug("Ignored exception while closing a destination.",
                                                     e);
                    }
                }
            }

            if (!stopOnly)
            {
                destinations.clear();
                destinations = null;

                dShutdownDestination = null;
            }
        }

        // stop receiving messages:
        if (connection != null)
        {
            if (JMSConnector.jmsLogger.isDebugEnabled())
            {
                JMSConnector.jmsLogger.debug("Closing JMS connection for destination manager: " +
                                             name);
            }

            try
            {
                connection.close();
            }
            catch (Throwable e)
            {
                if (JMSConnector.jmsLogger.isDebugEnabled())
                {
                    JMSConnector.jmsLogger.debug("Ignored exception while closing the the connection.",
                                                 e);
                }
            }

            connection = null;
        }

        if (!stopOnly)
        {
            initializedCorrectly = false;
        }

        if (JMSConnector.jmsLogger.isDebugEnabled())
        {
            JMSConnector.jmsLogger.debug("Destination manager closed successfully: " + name);
        }
    }

    /**
     * Returns the defaultCharset.
     *
     * @return  Returns the defaultCharset.
     */
    public Charset getDefaultCharset()
    {
        return defaultCharset;
    }

    /**
     * Returns the defaultErrorDestination.
     *
     * @return  Returns the defaultErrorDestination.
     */
    public Destination getDefaultErrorDestination()
    {
        return dDefaultErrorDestination;
    }

    /**
     * DOCUMENTME.
     *
     * @param   destinationName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  JMSConfigurationException  DOCUMENTME
     */
    public com.cordys.coe.ac.jmsconnector.Destination getDestination(String destinationName)
                                                              throws JMSConfigurationException
    {
        if (!initializedCorrectly)
        {
            throw new JMSConfigurationException("Destination Manager is not correctly configured, see error log for details");
        }

        return (com.cordys.coe.ac.jmsconnector.Destination) destinations.get(destinationName);
    }

    /**
     * Returns the dynamicDestination.
     *
     * @return  Returns the dynamicDestination.
     */
    public Destination getDynamicDestination()
    {
        return dDynamicDestination;
    }

    /**
     * DOCUMENTME.
     *
     * @return  DOCUMENTME
     */
    public String getName()
    {
        return name;
    }

    /**
     * DOCUMENTME.
     *
     * @return  DOCUMENTME
     */
    public int getTimeout()
    {
        return timeout;
    }

    /**
     * Returns the type.
     *
     * @return  Returns the type.
     */
    public Type getType()
    {
        return type;
    }

    /**
     * Try to get a connection to JMS. Restart destinations.
     *
     * @throws  GeneralException           In case of any exceptions
     * @throws  JMSException               In case of any exceptions
     * @throws  JMSConfigurationException  In case of any exceptions
     */
    public void restart()
                 throws GeneralException, JMSException, JMSConfigurationException
    {
        if (!initializedCorrectly)
        {
            return;
        }

        if (connection != null)
        {
            return;
        }

        connection = createConnection();

        if (connection != null)
        {
            boolean bStartedOk = false;

            try
            {
                if (JMSConnector.jmsLogger.isInfoEnabled())
                {
                    JMSConnector.jmsLogger.info(LogMessages.DESTINATIONMANAGER_RESTARTING, name);
                }

                // restart all destinations
                Enumeration<com.cordys.coe.ac.jmsconnector.Destination> enumDestinations = destinations
                                                                                           .elements();

                while (enumDestinations.hasMoreElements())
                {
                    enumDestinations.nextElement().restart();
                }
                connection.start();
                createExceptionListener();
                bStartedOk = true;
            }
            finally
            {
                if (!bStartedOk)
                {
                    close(true);
                }
            }
        }
    }

    /**
     * The defaultErrorDestination to set.
     *
     * @param  aDefaultErrorDestination  The defaultErrorDestination to set.
     */
    public void setDefaultErrorDestination(Destination aDefaultErrorDestination)
    {
        dDefaultErrorDestination = aDefaultErrorDestination;
    }

    /**
     * The dynamicDestination to set.
     *
     * @param  aDynamicDestination  The dynamicDestination to set.
     */
    public void setDynamicDestination(Destination aDynamicDestination)
    {
        dDynamicDestination = aDynamicDestination;
    }

    /**
     * DOCUMENTME.
     *
     * @throws  JMSException               In case of any exceptions
     * @throws  JMSConfigurationException  In case of any exceptions
     */
    public void start()
               throws JMSException, JMSConfigurationException
    {
        if (!initializedCorrectly)
        {
            return;
        }

        if (JMSConnector.jmsLogger.isInfoEnabled())
        {
            JMSConnector.jmsLogger.info(LogMessages.DESTINATIONMANAGER_STARTING, name);
        }

        // start all destinations
        Enumeration<com.cordys.coe.ac.jmsconnector.Destination> enumDestinations = destinations
                                                                                   .elements();

        while (enumDestinations.hasMoreElements())
        {
            enumDestinations.nextElement().start();
        }

        connection.start();

        createExceptionListener();

        if (JMSConnector.jmsLogger.isInfoEnabled())
        {
            JMSConnector.jmsLogger.log(Severity.INFO, "Destination manager " + name + " started.");
        }
    }

    /**
     * DOCUMENTME.
     *
     * @return  DOCUMENTME
     *
     * @throws  JMSException               In case of any exceptions
     * @throws  JMSConfigurationException  In case of any exceptions
     * @throws  JMSConnectorException      In case of any exceptions
     */
    protected Session createSession()
                             throws JMSException, JMSConfigurationException, JMSConnectorException
    {
        if (!initializedCorrectly)
        {
            throw new JMSConfigurationException("Destination Manager is not correctly configured, see error log for details");
        }

        // KE (2006/8/4): check connection first
        if ((connection == null) && bCheckConnectionOnRequest)
        {
            try
            {
                restart();
            }
            catch (Exception e)
            {
                boolean stopOnly = true;
                close(stopOnly);
                throw new JMSConnectorException(e,
                                                "Not able to restart Destination Manager " + name);
            }
        }

        if (connection == null)
        {
            throw new JMSConnectorException("Destination Manager lost connection to JMS provider");
        }

        // create a session transacted, and undefined acknowledge mode (not needed, because we use
        // transacted)
        return connection.createSession(true, 0);
    }

    /**
     * DOCUMENTME.
     *
     * @return  DOCUMENTME
     *
     * @throws  JMSException               In case of any exceptions
     * @throws  JMSConfigurationException  In case of any exceptions
     */
    Session createTriggerSession()
                          throws JMSException, JMSConfigurationException
    {
        if (!initializedCorrectly)
        {
            throw new JMSConfigurationException("Destination Manager is not correctly configured, see error log for details");
        }

        return connection.createSession(true, 0);
    }

    /**
     * Create a queue/topic connection.
     *
     * @return  DOCUMENTME
     *
     * @throws  JMSException  Thrown if the creation failed.
     */
    private Connection createConnection()
                                 throws JMSException
    {
    	Connection connection = null;
        if ((sConnectionUsername != null) && (sConnectionUsername.length() > 0) &&
                (sConnectionPassword != null))
        {
        	connection = cFactory.createConnection(sConnectionUsername, sConnectionPassword);
        }
        else
        {
        	connection = cFactory.createConnection();
        }
        //set clientID for publish subscriber. It will be set to the Destination Manager name
        connection.setClientID(this.getName());

        return connection;
    }

    /**
     * DOCUMENTME.
     *
     * @throws  JMSException  In case of any exceptions
     */
    private void createExceptionListener()
                                  throws JMSException
    {
        if ((dShutdownDestination == null) && !bCreateShutdownTempQueue)
        {
            if (JMSConnector.jmsLogger.isDebugEnabled())
            {
                JMSConnector.jmsLogger.debug("Shutdown destination is not configured. Not listening to manager shutdown.");
            }

            return;
        }

        sShutdownListenSession = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        javax.jms.Destination dJmsDest;

        if (dShutdownDestination != null)
        {
            if (JMSConnector.jmsLogger.isDebugEnabled())
            {
                JMSConnector.jmsLogger.debug("Using destination '" +
                                             dShutdownDestination.getName() +
                                             "' for shutdown listener.");
            }

            dJmsDest = dShutdownDestination.getInnerDestination();
        }
        else
        {
            if (JMSConnector.jmsLogger.isDebugEnabled())
            {
                JMSConnector.jmsLogger.debug("Creating a temporary queue for listening manager shutdown.");
            }

            tqShutdownListenQueue = sShutdownListenSession.createTemporaryQueue();
            dJmsDest = tqShutdownListenQueue;
        }

        // Create a message listener for this destination. The message selector is set so that
        // this listener will never receive messages.
        mcShutdownMessageConsumer = sShutdownListenSession.createConsumer(dJmsDest, "FALSE");
        mcShutdownMessageConsumer.setMessageListener(new MessageListener()
            {
                public void onMessage(Message mMsg)
                {
                    JMSConnector.jmsLogger.log(Severity.ERROR,
                                               "Shutdown listener received a message from the queue!.");
                }
            });

        connection.setExceptionListener(new ExceptionListener()
            {
                public void onException(JMSException e)
                {
                    handleJmsException(e);
                }
            });
    }

    // KE (2006/8/4): revised exception handler to shutdown
    // destination manager iso the soap processor
    /**
     * Exception handler on the JMS connection. When the provider is shut down, the exception
     * handler will be triggered, where the destination manager is stopped.
     *
     * @param  e  DOCUMENTME
     */
    private void handleJmsException(JMSException e)
    {
        if (JMSConnector.jmsLogger.isDebugEnabled())
        {
            JMSConnector.jmsLogger.debug("Received a queue exception. Assuming that it is the manager shutdown exception. " +
                                         "Stopping Destination Manager " + name, e);
        }

        if (bShutdownRequested)
        {
            if (JMSConnector.jmsLogger.isDebugEnabled())
            {
                JMSConnector.jmsLogger.debug("This manager is already shutting down. Ignoring the exception.");
            }

            return;
        }

        bShutdownRequested = true;

        // stop the destination manager
        try
        {
            boolean stopOnly = true;
            close(stopOnly);
        }
        catch (Exception e2)
        {
            // ignore
        }
        finally
        {
            bShutdownRequested = false;
        }
    }

    /**
     * DOCUMENTME.
     *
     * @param   config              DOCUMENTME
     * @param   parentJMXComponent  DOCUMENTME
     *
     * @throws  JMSConfigurationException  In case of any exceptions
     */
    private void initialize(JMSConnectorConfiguration config, IManagedComponent parentJMXComponent)
                     throws JMSConfigurationException
    {
        InitialContext jndiContext;

        managedComponent = parentJMXComponent.createSubComponent("DestinationManager", name,
                                                                 LogMessages.JMX_DESTINATION_MANAGER_SUBCOMPONENT_NAME,
                                                                 this);

        try
        {
            timeout = config.getDestinationManagerTimeout(name);

            Hashtable<String, String> destinationManagerContext = config
                                                                  .getDestinationManagerContext(name);

            jndiContext = new InitialContext(destinationManagerContext);
            type = Type.getByInitialContext(destinationManagerContext.get("java.naming.factory.initial"));
            cFactory = (ConnectionFactory) jndiContext.lookup(config.getDestinationManagerJNDIName(name));
            // get connection
            connection = createConnection();
        }
        catch (GeneralException e)
        {
            // fatal error, there is something wrong in the configuration xml, no setting but a
            // structural error
            throw new JMSConfigurationException(e,
                                                "Fatal error in configuration of destination manager " +
                                                name);
        }
        catch (CommunicationException e)
        {
            // configuration error, initial context could not be found or queue could not be found
            // in the initial context
            if (!config.getRunWithConfigurationError())
            {
                String err = StringFormatter.format(Locale.getDefault(),
                                                    LogMessages.DESTINATIONMANAGER_CONNECTION_ERROR,
                                                    name);

                throw new JMSConfigurationException(e, err);
            }
            else
            {
                JMSConnector.jmsLogger.error(e, LogMessages.DESTINATIONMANAGER_CONNECTION_ERROR,
                                             name);
            }

            return; // stop execution
        }
        catch (NamingException e)
        {
            // configuration error, initial context could not be found or queue could not be found
            // in the initial context
            if (!config.getRunWithConfigurationError())
            {
                String err = StringFormatter.format(Locale.getDefault(),
                                                    LogMessages.DESTINATIONMANAGER_JNDI_LOOKUP_FAILURE,
                                                    name);

                throw new JMSConfigurationException(e, err);
            }
            else
            {
                JMSConnector.jmsLogger.error(e, LogMessages.DESTINATIONMANAGER_JNDI_LOOKUP_FAILURE,
                                             name);
            }

            return; // stop execution
        }
        catch (JMSException e)
        {
            // configuration error, the destination is not completely correctly configured
            if (!config.getRunWithConfigurationError())
            {
                String err = StringFormatter.format(Locale.getDefault(),
                                                    LogMessages.DESTINATIONMANAGER_UNABLE_TO_INITIALIZE,
                                                    name);

                throw new JMSConfigurationException(e, err);
            }
            else
            {
                JMSConnector.jmsLogger.error(e, LogMessages.DESTINATIONMANAGER_UNABLE_TO_INITIALIZE,
                                             name);
            }

            return; // stop execution
        }

        initializedCorrectly = true;

        // get destinations
        String[] destinationNames = config.getDMDestinations(name);

        for (int i = 0; i < destinationNames.length; i++)
        {
            try
            {
                com.cordys.coe.ac.jmsconnector.Destination destination = new com.cordys.coe.ac
                                                                         .jmsconnector.Destination(jcAppConnector,
                                                                                                   this,
                                                                                                   config,
                                                                                                   destinationNames[i],
                                                                                                   managedComponent,
                                                                                                   jndiContext);
                destinations.put(destinationNames[i], destination);
            }
            catch (Exception e)
            {
                throw new JMSConfigurationException(e,
                                                    "Error while initializing destination " +
                                                    destinationNames[i] + " of manager " + name);
            }
        }

        String sShutdownDestName = null;

        try
        {
            sShutdownDestName = config.getDestinationManagerShutdownDestination(name);
        }
        catch (Exception ignored)
        {
        }

        if (sShutdownDestName != null)
        {
            sShutdownDestName = sShutdownDestName.trim();
        }

        if ((sShutdownDestName != null) && (sShutdownDestName.length() > 0))
        {
            if (!SHUTDOWN_TEMP_QUEUE_NAME.equals(sShutdownDestName))
            {
                dShutdownDestination = getDestination(sShutdownDestName);

                if (dShutdownDestination == null)
                {
                    throw new JMSConfigurationException("Shutdown destination not found: " +
                                                        sShutdownDestName);
                }

                if (!dShutdownDestination.canRead())
                {
                    throw new JMSConfigurationException("Shutdown destination is not readable: " +
                                                        sShutdownDestName);
                }

                if (dShutdownDestination.isDynamic())
                {
                    throw new JMSConfigurationException("Shutdown destination is a dynamic destination: " +
                                                        sShutdownDestName);
                }
            }
            else
            {
                bCreateShutdownTempQueue = true;
            }
        }
        else
        {
            dShutdownDestination = null;
        }

        bCheckConnectionOnRequest = config.getCheckConnectionOnRequest();
    }

    /**
     * DOCUMENTME.
     *
     * @author  $author$
     */
    enum Type
    {
        WEBSPHEREMQ("com.sun.jndi.fscontext.RefFSContextFactory"),
        ACTIVEMQ("org.apache.activemq.jndi.ActiveMQInitialContextFactory"),
        JBOSSMQ("org.jnp.interfaces.NamingContextFactory"),
        SUN_J2EE("com.sun.enterprise.naming.SerialInitContextFactory"),
        OPENJMS("org.exolab.jms.jndi.InitialContextFactory"),
        UNKNOWN;

        /**
         * DOCUMENTME.
         */
        private String initialContext;

        /**
         * Creates a new Type object.
         */
        private Type()
        {
        }

        /**
         * Creates a new Type object.
         *
         * @param  initialContext  DOCUMENTME
         */
        private Type(String initialContext)
        {
            this.initialContext = initialContext;
        }

        /**
         * DOCUMENTME.
         *
         * @param   initialContext  DOCUMENTME
         *
         * @return  DOCUMENTME
         */
        public static Type getByInitialContext(String initialContext)
        {
            for (Type t : values())
            {
                if ((t.initialContext != null) && t.initialContext.equals(initialContext))
                {
                    return t;
                }
            }

            return UNKNOWN;
        }
    }
}
