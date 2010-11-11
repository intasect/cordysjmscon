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
import com.cordys.coe.ac.jmsconnector.ext.EExtensionType;
import com.cordys.coe.ac.jmsconnector.ext.IProviderExtension;
import com.cordys.coe.ac.jmsconnector.messages.LogMessages;
import com.cordys.coe.coelib.LibraryVersion;

import com.cordys.parser.IParserEngine;
import com.cordys.parser.ParserEngineDispenser;

import com.eibus.connector.nom.Connector;

import com.eibus.localization.ILocalizableString;

import com.eibus.management.IManagedComponent;

import com.eibus.soap.ApplicationConnector;
import com.eibus.soap.ApplicationTransaction;
import com.eibus.soap.Processor;
import com.eibus.soap.SOAPTransaction;

import com.eibus.util.logger.CordysLogger;

import com.eibus.xml.nom.Document;
import com.eibus.xml.nom.Node;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import javax.jms.JMSException;

/**
 * DOCUMENTME.
 */
/**
 * DOCUMENTME
 * .
 *
 * @author  awisse
 */
public class JMSConnector extends ApplicationConnector
{
    /**
     * Holds the name of the connector.
     */
    private static final String CONNECTOR_NAME = "JMSConnector Connector";
    /**
     * Contains the logger.
     */
    public static CordysLogger jmsLogger = CordysLogger.getCordysLogger(JMSConnector.class);
    /**
     * Holds the configuration object for this connector.
     */
    private JMSConnectorConfiguration acConfiguration;

    /**
     * Indicated if the JMS connections are opened or not.
     */
    private boolean bConnectionsOpened = false;
    /**
     * If <code>true</code> the process terminates if an error occurred in initialization.
     */
    private boolean bTerminateOnInitError = true;

    /**
     * Holds the connector to use for sending messages to Cordys.
     */
    private Connector cConnector;

    /**
     * DOCUMENTME.
     */
    private JMSConnectionPoller jmsConnectionPoller;
    /**
     * Contains provider extensions. If null, none is set.
     */
    private List<IProviderExtension> lProviderExtensions = null;

    /**
     * DOCUMENTME.
     */
    private ParserEngineDispenser m_parserEngineDispenser;

    /**
     * DOCUMENTME.
     */
    private Hashtable<String, DestinationManager> m_registeredDMList;
    /**
     * Contains a shared NOM document for parsing XML.
     */
    private Document sharedNomDocument;

    /**
     * Check all destination managers wrt the jms connection.
     */
    public void checkDestManagerConnections()
    {
        String[] destinationManagers = acConfiguration.getDestinationManagers();

        for (int i = 0; i < destinationManagers.length; i++)
        {
            DestinationManager destManager = (DestinationManager) m_registeredDMList.get(destinationManagers[i]);
            destManager.checkConnection();
        }
    }

    /**
     * This method gets called when the processor is being stopped.
     *
     * @param  pProcessor  The processor that is being stopped.
     */
    @Override public void close(Processor pProcessor)
    {
        closeJmsConnection(true);

        if (jmsLogger.isInfoEnabled())
        {
            jmsLogger.info(LogMessages.CONNECTOR_STOPPED);
        }
    }

    /**
     * Closes all JMS connections to destination managers.
     *
     * @param  connectorStopping  <code>true</code> if the connector is stopping
     */
    public synchronized void closeJmsConnection(boolean connectorStopping)
    {
        if (jmsLogger.isDebugEnabled())
        {
            jmsLogger.debug("Closing all destination managers.");
        }

        boolean stopOnly = false;

        if (m_registeredDMList != null)
        {
            Enumeration<String> destinationManagers = m_registeredDMList.keys();

            while (destinationManagers.hasMoreElements())
            {
                String key = destinationManagers.nextElement();

                try
                {
                    m_registeredDMList.get(key).close(stopOnly, connectorStopping);
                }
                catch (Exception e)
                {
                    if (JMSConnector.jmsLogger.isDebugEnabled())
                    {
                        JMSConnector.jmsLogger.debug("Ignored exception while closing a destination manager.",
                                                     e);
                    }
                }
            }

            m_registeredDMList.clear();
            m_registeredDMList = null;
        }

        if (jmsLogger.isDebugEnabled())
        {
            jmsLogger.debug("Destination managers closed successfully.");
        }

        bConnectionsOpened = false;
    }

    /**
     * This method creates the transaction that will handle the requests.
     *
     * @param   stTransaction  The SOAP-transaction containing the message.
     *
     * @return  The newly created transaction.
     */
    @Override public ApplicationTransaction createTransaction(SOAPTransaction stTransaction)
    {
        return new JMSConnectorTransaction(this);
    }

    /**
     * Returns the Connector instance for this processor.
     *
     * @return  the connector
     */
    public Connector getConnector()
    {
        return cConnector;
    }

    /**
     * Get a destination by the given URI.
     *
     * @param   uri  the uri to get the destination for
     *
     * @return  a destination
     *
     * @throws  JMSConfigurationException  The processor was not configured properly
     */
    public Destination getDestinationByURI(String uri)
                                    throws JMSConfigurationException
    {
        if (m_registeredDMList == null)
        {
            throw new JMSConfigurationException("Destination managers are not opened.");
        }

        int iPos = uri.indexOf(".");

        if ((iPos < 1) || (iPos >= (uri.length() - 1)))
        {
            return null;
        }

        String sDestMananager = uri.substring(0, iPos);
        String sDest = uri.substring(iPos + 1);

        DestinationManager dm = (DestinationManager) m_registeredDMList.get(sDestMananager);

        if (dm == null)
        {
            return null;
        }

        return dm.getDestination(sDest);
    }

    /**
     * Returns a parser engine for the given protocol.
     *
     * @param   protocol  the protocol to get the parser engine for
     *
     * @return  the parser engine
     *
     * @throws  JMSConnectorException  The given protocol was not found
     */
    public IParserEngine getParserEngine(String protocol)
                                  throws JMSConnectorException
    {
        IParserEngine pe = m_parserEngineDispenser.getParserEngine(protocol);

        if (pe == null)
        {
            throw new JMSConnectorException("Unknown messageformat '" + protocol + "'");
        }
        return pe;
    }

    /**
     * Returns a list of provider extensions.
     *
     * @return  A list of provider extensions or <code>null</code> if none is set.
     */
    public List<IProviderExtension> getProviderExtensions()
    {
        return lProviderExtensions;
    }

    /**
     * Returns the sharedNomDocument.
     *
     * @return  Returns the sharedNomDocument.
     */
    public Document getSharedNomDocument()
    {
        return sharedNomDocument;
    }

    /**
     * Returns <code>true</code> if the JMS connections have been opened.
     *
     * @return  <code>true</code> if the JMS connections have been opened.
     */
    public synchronized boolean isJmsConnectionOpen()
    {
        return bConnectionsOpened;
    }

    /**
     * Returns the terminateOnInitError.
     *
     * @return  Returns the terminateOnInitError.
     */
    public boolean isTerminateOnInitError()
    {
        return bTerminateOnInitError;
    }

    /**
     * This method gets called when the processor is started. It reads the configuration of the
     * processor and creates the connector with the proper parameters. It will also create a client
     * connection to Cordys.
     *
     * @param  pProcessor  The processor that is started.
     */
    @Override public void open(Processor pProcessor)
    {
        // Check the CoELib version.
        try
        {
            LibraryVersion.loadAndCheckLibraryVersionFromResource(this.getClass(), true);
        }
        catch (Exception e)
        {
            jmsLogger.fatal(e, LogMessages.COELIB_VERSION_MISMATCH);
            throw new IllegalStateException(e.toString());
        }

        try
        {
            if (jmsLogger.isInfoEnabled())
            {
                jmsLogger.info(LogMessages.CONNECTOR_STARTING);
            }

            // Get the configuration
            int configurationXmlNode = getConfiguration();

            sharedNomDocument = Node.getDocument(configurationXmlNode);
            acConfiguration = new JMSConnectorConfiguration(configurationXmlNode, this);
            acConfiguration.inlineAllTriggerParameterXmls();

            // Open the client connector
            cConnector = Connector.getInstance(CONNECTOR_NAME);

            if (!cConnector.isOpen())
            {
                cConnector.open();
            }

            // initialize the BTC parser
            m_parserEngineDispenser = ParserEngineDispenser.getInstance();
            m_parserEngineDispenser.loadParserEngines(acConfiguration.getBTConfiguration());

            loadProviderExtensions();

            // Initialize the JMS destination managers.
            openJmsConnection();

            // KE (2006/8/4): creation of jms connections poller added:
            // Start the jms connections poller thread
            jmsConnectionPoller = new JMSConnectionPoller(this,
                                                          acConfiguration.getJMSPollingInterval());

            Thread pollerThread = new Thread(jmsConnectionPoller);
            pollerThread.start();

            if (jmsLogger.isInfoEnabled())
            {
                jmsLogger.info(LogMessages.CONNECTOR_STARTED);
            }
        }
        catch (Exception e)
        {
            jmsLogger.fatal(e, LogMessages.CONNECTOR_START_EXCEPTION);

            if (bTerminateOnInitError)
            {
                // TODO: How to shut down the SOAP processor gracefully??
                if (e instanceof JMSConfigurationException)
                {
                    System.exit(Processor.EXIT_14); // Configuration error.
                }
                else
                {
                    System.exit(Processor.EXIT_12); // Connection error.
                }
            }
            else
            {
                throw new RuntimeException("Initialization failed.", e);
            }
        }
    }

    /**
     * Open all the JMS connections to destination managers.
     *
     * @throws  JMSConfigurationException  Thrown if the configuration contained error(s).
     * @throws  JMSException               Thrown if the connection could not be opened.
     */
    public synchronized void openJmsConnection()
                                        throws JMSConfigurationException, JMSException
    {
        if (bConnectionsOpened)
        {
            return;
        }

        if (jmsLogger.isDebugEnabled())
        {
            jmsLogger.debug("Initializing and starting all destination managers.");
        }

        if (m_registeredDMList != null)
        {
            throw new JMSException("JMS connection is already open.");
        }

        boolean bSuccess = false;

        try
        {
            // load the configured destination managers
            m_registeredDMList = new Hashtable<String, DestinationManager>();

            String[] destinationManagers = acConfiguration.getDestinationManagers();
            IManagedComponent mc = getManagedComponent();

            for (int i = 0; i < destinationManagers.length; i++)
            {
                DestinationManager destManager = new DestinationManager(this, acConfiguration,
                                                                        destinationManagers[i], mc);
                m_registeredDMList.put(destinationManagers[i], destManager);
            }

            // start them
            for (int i = 0; i < destinationManagers.length; i++)
            {
                DestinationManager destManager = (DestinationManager) m_registeredDMList.get(destinationManagers[i]);
                destManager.start();
            }

            bSuccess = true;
        }
        finally
        {
            if (!bSuccess)
            {
                closeJmsConnection(true);
            }
        }

        if (jmsLogger.isDebugEnabled())
        {
            jmsLogger.debug("Destination managers started successfully.");
        }

        bConnectionsOpened = true;
    }

    /**
     * This method gets called when the processor is ordered to reset.
     *
     * @param  processor  The processor that is to be in reset state
     */
    @Override public void reset(Processor processor)
    {
        if (jmsLogger.isInfoEnabled())
        {
            jmsLogger.info(LogMessages.CONNECTOR_RESET);
        }
    }

    /**
     * The terminateOnInitError to set.
     *
     * @param  aTerminateOnInitError  The terminateOnInitError to set.
     */
    public void setTerminateOnInitError(boolean aTerminateOnInitError)
    {
        bTerminateOnInitError = aTerminateOnInitError;
    }

    // JMX implementation:
    /**
     * @see  com.eibus.soap.ApplicationConnector#createManagedComponent()
     */
    @Override protected IManagedComponent createManagedComponent()
    {
        IManagedComponent mc = super.createManagedComponent();

        return mc;
    }

    /**
     * @see  com.eibus.soap.ApplicationConnector#getManagedComponentType()
     */
    @Override protected String getManagedComponentType()
    {
        return "AppConnector";
    }

    /**
     * @see  com.eibus.soap.ApplicationConnector#getManagementDescription()
     */
    @Override protected ILocalizableString getManagementDescription()
    {
        return LogMessages.CONNECTOR_MANAGEMENT_DESCRIPTION;
    }

    /**
     * @see  com.eibus.soap.ApplicationConnector#getManagementName()
     */
    @Override protected String getManagementName()
    {
        return "JMS Connector";
    }

    /**
     * Loads all configured provider extensions. Extensions are enabled by a JVM argument, e.g:
     * -Djmsconnector.extensions=WSMQ_SETMESSAGEID
     *
     * @throws  JMSConnectorException  In case of any exceptions
     */
    private void loadProviderExtensions()
                                 throws JMSConnectorException
    {
        String sExtStr = System.getProperty("jmsconnector.extensions");

        if (sExtStr == null)
        {
            return;
        }

        String[] saExts = sExtStr.split(",");

        for (String sExtName : saExts)
        {
            EExtensionType etType = EExtensionType.valueOf(sExtName.trim());

            if (etType == null)
            {
                throw new JMSConnectorException("Invalid provider extension name: " + sExtName);
            }

            if (jmsLogger.isDebugEnabled())
            {
                jmsLogger.debug("Loading provider extension: " + etType);
            }

            IProviderExtension peExtension;

            try
            {
                Class<?> cExtClass;

                cExtClass = Class.forName(etType.getClassName());
                peExtension = (IProviderExtension) cExtClass.newInstance();
            }
            catch (Throwable e)
            {
                throw new JMSConnectorException(e,
                                                "Unable to instantiate provider extension: " +
                                                sExtName);
            }

            if (peExtension.initialize(this))
            {
                if (lProviderExtensions == null)
                {
                    lProviderExtensions = new ArrayList<IProviderExtension>(10);
                }

                lProviderExtensions.add(peExtension);
            }
        }
    }
}
