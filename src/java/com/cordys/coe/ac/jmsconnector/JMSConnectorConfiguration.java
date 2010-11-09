/**
 * � 2004 Cordys R&D B.V. All rights reserved.
 * The computer program(s) is the proprietary information of Cordys R&D B.V.
 * and provided under the relevant License Agreement containing restrictions
 * on use and disclosure. Use is subject to the License Agreement.
 */
package com.cordys.coe.ac.jmsconnector;

import com.cordys.coe.ac.jmsconnector.exceptions.JMSConnectorException;
import com.cordys.coe.exception.GeneralException;
import com.cordys.coe.util.XMLProperties;

import com.eibus.directory.soap.DirectoryException;

import com.eibus.util.Base64;
import com.eibus.util.logger.Severity;

import com.eibus.xml.nom.Find;
import com.eibus.xml.nom.Node;
import com.eibus.xml.nom.NodeType;
import com.eibus.xml.nom.XMLException;

import java.io.UnsupportedEncodingException;

import java.util.Hashtable;

/**
 * This class holds the configuration details for the JMSConnector.
 */
public class JMSConnectorConfiguration
{
    /**
     * Default destination manager timeout.
     */
    public static final int DEFAULT_TIMEOUT = 20000;
    /**
     * Holds the default value whether or not to disable the message selector. It defaults to false,
     * but can be influenced by setting the system property jmsconnector.disable.message.selector to
     * true.
     */
    private static final boolean DEFAULT_DISABLE_MESSAGE_SELECTOR = Boolean.parseBoolean(System
                                                                                         .getProperty("jmsconnector.disable.message.selector",
                                                                                                      "false"));
    /**
     * DOCUMENTME.
     */
    private JMSConnector jmsConnector;
    /**
     * Holds the XMLProperties object to extract the value for different configuration keys.
     */
    private XMLProperties xpBase;
    /**
     * Holds an empty property object used when no configuration element was found. This is a
     * work-around to fix null pointer exceptions in the code.
     */
    private XMLProperties xpDummy;

    /**
     * Creates the constructor.This loads the configuration object and pass it to XMLProperties for
     * processing.
     *
     * @param   iConfigNode   The xml-node that contains the configuration.
     * @param   jmsConnector  DOCUMENTME
     *
     * @throws  JMSConnectorException  DOCUMENTME
     */
    public JMSConnectorConfiguration(int iConfigNode, JMSConnector jmsConnector)
                              throws JMSConnectorException
    {
        if (iConfigNode == 0)
        {
            throw new JMSConnectorException("Configuration not found");
        }

        if (!Node.getName(iConfigNode).equals("configuration"))
        {
            throw new JMSConnectorException("Root-tag of the configuration should be <configuration>");
        }

        try
        {
            xpBase = new XMLProperties(iConfigNode);

            // Create dummy XML section.
            int xDummyNode = Node.createElement("__dummy-cfg-element__", iConfigNode);

            xpDummy = new XMLProperties(xDummyNode);

            this.jmsConnector = jmsConnector;
        }
        catch (GeneralException e)
        {
            throw new JMSConnectorException(e,
                                            "Exception while creating the configuration-object.");
        }
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName      DOCUMENTME
     * @param   destinationName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  DirectoryException  In case of any exceptions
     * @throws  GeneralException    In case of any exceptions
     */
    public TriggerMessageInfo createMethodForInboundTrigger(String managerName,
                                                            String destinationName)
                                                     throws DirectoryException, GeneralException
    {
        XMLProperties triggerProperties = getTriggerProperties(getDestinationProperties(managerName,
                                                                                        destinationName)
                                                               .getStringValue("inboundmessagetrigger"));

        if (triggerProperties == null)
        {
            return null;
        }

        if (getDestinationHasTrigger(managerName, destinationName))
        {
            String method = triggerProperties.getStringValue("method");
            String namespace = triggerProperties.getStringValue("namespace");
            String oDN = triggerProperties.getStringValue("odn");
            String uDN = triggerProperties.getStringValue("udn");
            long timeout = 30000L;

            // Parse the timeout.
            try
            {
                String str = triggerProperties.getStringValue("request-timeout");

                if ((str != null) && (str.length() > 0))
                {
                    timeout = Long.parseLong(str);
                }
            }
            catch (Exception e)
            {
                JMSConnector.jmsLogger.log(Severity.WARN, "Unable to parse trigger timeout.", e);
            }

            int soapMethod = jmsConnector.getConnector().createSOAPMethod(uDN, oDN, namespace,
                                                                          method);
            // int soapMethod = jmsConnector.getConnector().createSOAPMethodEx( namespace, method,
            // oDN, uDN, null, null );

            int parameters = Node.clone(triggerProperties.getXMLNode("parameters"), true);

            if (parameters != 0)
            {
                Node.appendToChildren(Node.getFirstChild(parameters), Node.getLastChild(parameters),
                                      soapMethod);

                int attrCount = Node.getNumAttributes(parameters);

                for (int i = 0; i < attrCount; i++)
                {
                    String attrName = Node.getAttributeName(parameters, i + 1);

                    Node.setAttribute(soapMethod, attrName,
                                      Node.getAttribute(parameters, attrName));
                }

                // Delete the root parameters node.
                Node.delete(parameters);
                parameters = 0;
            }

            TriggerMessageInfo res = new TriggerMessageInfo();

            res.soapRequestNode = Node.getRoot(soapMethod);
            res.requestTimeout = timeout;

            return res;
        }

        return null;
    }

    /**
     * This method returns whether or not the message selectors should be disabled even if the JMS
     * type supports it. When set to true the connector will internally maintain whether or not the
     * message was already processed.
     *
     * @return  Whether or not the message selectors should be disabled even if the JMS type
     *          supports it.
     */
    public boolean disableMessageSelector()
    {
        return xpBase.getBooleanValue("disable-message-selectors",
                                      DEFAULT_DISABLE_MESSAGE_SELECTOR);
    }

    /**
     * DOCUMENTME.
     *
     * @return  DOCUMENTME
     */
    public int getBTConfiguration()
    {
        return xpBase.getXMLNode("binarytransformationconfig");
    }

    /**
     * DOCUMENTME.
     *
     * @return  DOCUMENTME
     */
    public boolean getCheckConnectionOnRequest()
    {
        return xpBase.getBooleanValue("check-connection-on-request");
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName      DOCUMENTME
     * @param   destinationName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public String getDestinationAuthentication(String managerName, String destinationName)
                                        throws GeneralException
    {
        return getDestinationProperties(managerName, destinationName).getStringValue("authenticationtype");
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName      DOCUMENTME
     * @param   destinationName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public String getDestinationAuthenticationPassword(String managerName, String destinationName)
                                                throws GeneralException
    {
        String sPwd = getDestinationProperties(managerName, destinationName).getStringValue("password");

        if (sPwd == null)
        {
            return null;
        }

        return Base64.decode(sPwd);
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName      DOCUMENTME
     * @param   destinationName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public String getDestinationAuthenticationUsername(String managerName, String destinationName)
                                                throws GeneralException
    {
        return getDestinationProperties(managerName, destinationName).getStringValue("username");
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName      DOCUMENTME
     * @param   destinationName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public String getDestinationBTProtocol(String managerName, String destinationName)
                                    throws GeneralException
    {
        String protocol = getDestinationProperties(managerName, destinationName).getStringValue("btcprotocol");

        if ((protocol == null) || protocol.equals("none"))
        {
            return null;
        }
        return protocol;
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName      DOCUMENTME
     * @param   destinationName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public String getDestinationCharacterSet(String managerName, String destinationName)
                                      throws GeneralException
    {
        return getDestinationProperties(managerName, destinationName).getStringValue("charset");
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName      DOCUMENTME
     * @param   destinationName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public String getDestinationErrorDestinationReference(String managerName,
                                                          String destinationName)
                                                   throws GeneralException
    {
        String ref = getDestinationProperties(managerName, destinationName).getStringValue("errordestination");

        if (ref.equals("none"))
        {
            return null;
        }
        return ref;
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName      DOCUMENTME
     * @param   destinationName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public boolean getDestinationHasReadAccess(String managerName, String destinationName)
                                        throws GeneralException
    {
        return getDestinationProperties(managerName, destinationName).getStringValue("access")
                                                                     .indexOf("read") > -1;
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName      DOCUMENTME
     * @param   destinationName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public boolean getDestinationHasTrigger(String managerName, String destinationName)
                                     throws GeneralException
    {
        String sValue = getDestinationProperties(managerName, destinationName).getStringValue("inboundmessagetrigger");

        return (sValue != null) && !sValue.equals("none");
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName      DOCUMENTME
     * @param   destinationName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public boolean getDestinationHasWriteAccess(String managerName, String destinationName)
                                         throws GeneralException
    {
        return getDestinationProperties(managerName, destinationName).getStringValue("access")
                                                                     .indexOf("write") > -1;
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName      DOCUMENTME
     * @param   destinationName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public String getDestinationJNDIName(String managerName, String destinationName)
                                  throws GeneralException
    {
        return getDestinationProperties(managerName, destinationName).getStringValue("jndiname");
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public String getDestinationManagerAuthentication(String managerName)
                                               throws GeneralException
    {
        return getDestinationManagerProperties(managerName).getStringValue("authenticationtype");
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public String getDestinationManagerAuthenticationPassword(String managerName)
                                                       throws GeneralException
    {
        return getDestinationManagerProperties(managerName).getStringValue("password");
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public String getDestinationManagerAuthenticationUsername(String managerName)
                                                       throws GeneralException
    {
        return getDestinationManagerProperties(managerName).getStringValue("username");
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public String getDestinationManagerCharacterSet(String managerName)
                                             throws GeneralException
    {
        return getDestinationManagerProperties(managerName).getStringValue("charset");
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public String getDestinationManagerConnectionPassword(String managerName)
                                                   throws GeneralException
    {
        String sPwd = getDestinationManagerProperties(managerName).getStringValue("connection-password");

        if (sPwd == null)
        {
            return null;
        }

        return Base64.decode(sPwd);
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public String getDestinationManagerConnectionUser(String managerName)
                                               throws GeneralException
    {
        return getDestinationManagerProperties(managerName).getStringValue("connection-username");
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public String getDestinationManagerJNDIName(String managerName)
                                         throws GeneralException
    {
        return getDestinationManagerProperties(managerName).getStringValue("jndiCFname");
    }

    /**
     * DOCUMENTME.
     *
     * @return  DOCUMENTME
     */
    public String[] getDestinationManagers()
    {
        String[] brokers;
        int managers = xpBase.getXMLNode("DestinationManagers");
        brokers = new String[Node.getNumChildren(managers)];

        int i = 0;

        for (int nManager = Node.getFirstChild(managers); nManager != 0;
                 nManager = Node.getNextSibling(nManager))
        {
            brokers[i++] = Node.getAttribute(nManager, "name");
        }

        return brokers;
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public int getDestinationManagerTimeout(String managerName)
                                     throws GeneralException
    {
        return parseTimeoutValue(getDestinationManagerProperties(managerName).getStringValue("timeout",
                                                                                             xpBase
                                                                                             .getStringValue("timeout")));
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName      DOCUMENTME
     * @param   destinationName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public String getDestinationMessageSelector(String managerName, String destinationName)
                                         throws GeneralException
    {
        return getDestinationProperties(managerName, destinationName).getStringValue("message-selector");
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName  DOCUMENTME
     *
     * @return  DOCUMENTME
     */
    public String[] getDMDestinations(String managerName)
    {
        String[] destinations;
        int[] dests = Find.match(xpBase.getXMLNode("DestinationManagers"),
                                 "<><DestinationManager name=\"" + managerName +
                                 "\"><Destination>");
        destinations = new String[dests.length];

        for (int i = 0; i < dests.length; i++)
        {
            destinations[i] = Node.getAttribute(dests[i], "name");
        }

        return destinations;
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName      DOCUMENTME
     * @param   destinationName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public String getDynamicDestinationParameterString(String managerName, String destinationName)
                                                throws GeneralException
    {
        return getDestinationProperties(managerName, destinationName).getStringValue("dynamic-dest-parameters");
    }

    /**
     * KE (2006/08/04): added
     *
     * @return  DOCUMENTME
     */
    public double getJMSPollingInterval()
    {
        String sValue = xpBase.getStringValue("jmspollinginterval", "");

        if (sValue.length() == 0)
        {
            return 30; // Return some sane default.
        }

        return Double.parseDouble(sValue);
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public String getJMSVendor(String managerName)
                        throws GeneralException
    {
        return getDestinationManagerProperties(managerName).getStringValue("jmsvendor");
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public String getProviderURL(String managerName)
                          throws GeneralException
    {
        return getDestinationManagerProperties(managerName).getStringValue("providerURL");
    }

    /**
     * DOCUMENTME.
     *
     * @return  DOCUMENTME
     */
    public boolean getRunWithConfigurationError()
    {
        return xpBase.getBooleanValue("runWithConfigError");
    }

    /**
     * DOCUMENTME.
     *
     * @return  DOCUMENTME
     */
    public long getSOAPProcessorTimeout()
    {
        return parseTimeoutValue(xpBase.getStringValue("timeout"));
    }

    /**
     * DOCUMENTME.
     *
     * @param   triggerId  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  DirectoryException  DOCUMENTME
     * @throws  GeneralException    DOCUMENTME
     */
    public String getTriggerCharacterSet(String triggerId)
                                  throws DirectoryException, GeneralException
    {
        XMLProperties triggerProperties = getTriggerProperties(triggerId);

        if (triggerProperties != null)
        {
            return triggerProperties.getStringValue("charset");
        }

        return null;
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName      DOCUMENTME
     * @param   destinationName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public String getTriggerNameForDestination(String managerName, String destinationName)
                                        throws GeneralException
    {
        return getDestinationProperties(managerName, destinationName).getStringValue("inboundmessagetrigger");
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName      DOCUMENTME
     * @param   destinationName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public int getTriggerThreadCountForDestination(String managerName, String destinationName)
                                            throws GeneralException
    {
        String sValue = getDestinationProperties(managerName, destinationName).getStringValue("num-trigger-listeners");

        try
        {
            return Integer.parseInt(sValue);
        }
        catch (NumberFormatException e)
        {
            return 1;
        }
    }

    /**
     * Creates parameters XML for the given trigger if this the XML is as a encoded text inside the
     * parameters element (this is the new version).
     *
     * @throws  UnsupportedEncodingException
     * @throws  XMLException
     */
    public void inlineAllTriggerParameterXmls()
                                       throws UnsupportedEncodingException, XMLException
    {
        int[] xaTriggerParams = Find.match(xpBase.getConfigNode(),
                                           "<><Triggers><Trigger><parameters>");

        for (int xParams : xaTriggerParams)
        {
            // Check if the parameters node is XML or escaped XML. New version of the config page
            // uses escaped XML to fix problems with tuple elements in the trigger XML.
            int xTmp = Node.getFirstChild(xParams);

            if ((xTmp != 0) &&
                    ((Node.getType(xTmp) == NodeType.CDATA) ||
                         (Node.getType(xTmp) == NodeType.DATA)))
            {
                // Parse the parameter text into XML.
                String sData = Node.getData(xParams);
                int xNewParams;

                // Clear all content.
                while ((xTmp = Node.getFirstChild(xParams)) != 0)
                {
                    Node.delete(xTmp);
                    xTmp = 0;
                }

                xNewParams = Node.getDocument(xParams).parseString(sData);
                Node.appendToChildren(Node.getFirstChild(xNewParams), Node.getLastChild(xNewParams),
                                      xParams);
                Node.delete(xNewParams);
                xNewParams = 0;
            }
        }
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName      DOCUMENTME
     * @param   destinationName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public boolean isDestinationDefaultErrorDestination(String managerName, String destinationName)
                                                 throws GeneralException
    {
        return "true".equals(getDestinationProperties(managerName, destinationName).getStringValue("is-default-error-dest"));
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName      DOCUMENTME
     * @param   destinationName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    public boolean isDestinationDynamic(String managerName, String destinationName)
                                 throws GeneralException
    {
        return "true".equals(getDestinationProperties(managerName, destinationName).getStringValue("is-dynamic"));
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    protected Hashtable<String, String> getDestinationManagerContext(String managerName)
                                                              throws GeneralException
    {
        Hashtable<String, String> env = new Hashtable<String, String>();
        String sProviderUrl = getProviderURL(managerName);
        String sVendor = getJMSVendor(managerName);

        if ((sProviderUrl == null) || (sProviderUrl.length() == 0))
        {
            throw new GeneralException("Provider URL not set for destination manager: " +
                                       managerName);
        }

        if ((sVendor == null) || (sVendor.length() == 0))
        {
            throw new GeneralException("JMS vendor not set for destination manager: " +
                                       managerName);
        }

        env.put("java.naming.factory.initial", sVendor);
        env.put("java.naming.provider.url", sProviderUrl);

        String authentication = getDestinationManagerAuthentication(managerName);
        env.put("java.naming.security.authentication", authentication);

        if (!"none".equals(authentication))
        {
            env.put("java.naming.security.principal",
                    getDestinationManagerAuthenticationUsername(managerName));
            env.put("java.naming.security.credentials",
                    getDestinationManagerAuthenticationPassword(managerName));
        }
        return env;
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    protected XMLProperties getDestinationManagerProperties(String managerName)
                                                     throws GeneralException
    {
        XMLProperties xpRes = xpBase.getXMLProperties("DestinationManagers><DestinationManager name=\"" +
                                                      managerName + "\"");

        if (xpRes == null)
        {
            xpRes = xpDummy;
        }

        return xpRes;
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    protected String getDestinationManagerShutdownDestination(String managerName)
                                                       throws GeneralException
    {
        return getDestinationManagerProperties(managerName).getStringValue("shutdown-destination");
    }

    /**
     * DOCUMENTME.
     *
     * @param   managerName      DOCUMENTME
     * @param   destinationName  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    protected XMLProperties getDestinationProperties(String managerName, String destinationName)
                                              throws GeneralException
    {
        XMLProperties xpRes = xpBase.getXMLProperties("DestinationManagers><DestinationManager name=\"" +
                                                      managerName + "\"><Destination name=\"" +
                                                      destinationName + "\"");

        if (xpRes == null)
        {
            xpRes = xpDummy;
        }

        return xpRes;
    }

    /**
     * DOCUMENTME.
     *
     * @param   triggerId  DOCUMENTME
     *
     * @return  DOCUMENTME
     *
     * @throws  GeneralException  DOCUMENTME
     */
    private XMLProperties getTriggerProperties(String triggerId)
                                        throws GeneralException
    {
        return xpBase.getXMLProperties("Triggers><Trigger name=\"" + triggerId + "\"");
    }

    /**
     * DOCUMENTME.
     *
     * @param   sValue  DOCUMENTME
     *
     * @return  DOCUMENTME
     */
    private int parseTimeoutValue(String sValue)
    {
        if (sValue == null)
        {
            return -1;
        }

        sValue = sValue.trim();

        if (sValue.length() == 0)
        {
            return -1;
        }

        // Remove any separators.
        sValue = sValue.replaceAll("[.]", "");

        // Parse the value.
        int iValue = Integer.parseInt(sValue);

        return (iValue >= 0) ? iValue : DEFAULT_TIMEOUT;
    }

    /**
     * DOCUMENTME.
     *
     * @author  $author$
     */
    public static class TriggerMessageInfo
    {
        /**
         * DOCUMENTME.
         */
        public long requestTimeout;
        /**
         * DOCUMENTME.
         */
        public int soapRequestNode;
    }
}
