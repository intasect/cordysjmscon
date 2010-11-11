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

import com.cordys.coe.ac.jmsconnector.exceptions.JMSConnectorException;

import com.eibus.util.Base64;

import com.eibus.xml.nom.Node;

import java.io.PrintWriter;
import java.io.StringWriter;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;

/**
 * DOCUMENTME
 * .
 *
 * @author  awisse
 */
public class JMSUtil
{
    /**
     * Appends extra parameters to a dynamic destination URL. The format of this URL is provider
     * specific and probably not supported by all providers. Currently this method is hard-coded for
     * WebSphere MQ.
     *
     * @param   sPhysicalName  Physical URL of the destination.
     * @param   dDest          Dynamic JMSConnector destination.
     * @param   sParamString   Extra parameters as configured in the connector configuration page.
     *
     * @return
     */
    public static String appendDynamicDestinationParameters(String sPhysicalName, Destination dDest,
                                                            String sParamString)
    {
        if ((sParamString == null) || (sParamString.length() == 0))
        {
            return sPhysicalName;
        }

        // Parse them in to a hash map, so later we can do parameter merging.
        Map<String, String> mParams = new HashMap<String, String>();
        String[] saTmpArray = sParamString.split(",");

        for (String sTmp : saTmpArray)
        {
            int iPos = sTmp.indexOf('=');
            String sName;
            String sValue;

            if (iPos > 0)
            {
                sName = sTmp.substring(0, iPos).trim();
                sValue = ((iPos < (sTmp.length() - 1)) ? sTmp.substring(iPos + 1).trim() : "");
            }
            else
            {
                sName = sTmp;
                sValue = null;
            }

            mParams.put(sName, sValue);
        }

        // Discard the possible query part from the physical name.
        int iPos = sPhysicalName.indexOf('?');

        if (iPos >= 0)
        {
            sPhysicalName = sPhysicalName.substring(0, iPos);
        }

        StringBuilder sbRes = new StringBuilder(100);
        boolean bFirst = true;

        sbRes.append(sPhysicalName);

        for (Map.Entry<String, String> eEntry : mParams.entrySet())
        {
            if (bFirst)
            {
                sbRes.append('?');
                bFirst = false;
            }
            else
            {
                sbRes.append('&');
            }

            String sName = eEntry.getKey();
            String sValue = eEntry.getValue();

            sbRes.append(sName);

            if (sValue != null)
            {
                sbRes.append('=');
                sbRes.append(sValue);
            }
        }

        return sbRes.toString();
    }

    /**
     * DOCUMENTME.
     *
     * @param   str  DOCUMENTME
     *
     * @return  DOCUMENTME
     */
    public static byte[] base64decode(String str)
    {
        return Base64.decode(str.toCharArray());
    }

    /**
     * DOCUMENTME.
     *
     * @param   data  DOCUMENTME
     *
     * @return  DOCUMENTME
     */
    public static String base64encode(byte[] data)
    {
        return new String(Base64.encode(data));
    }

    /**
     * Finds the cause of an exception.
     *
     * @param   t  the exception to look for the cause
     *
     * @return  the cause
     */
    public static String findCause(Throwable t)
    {
        String msg = t.getMessage();

        if (((msg == null) || "".equals(msg)) && (t.getCause() != null))
        {
            msg = findCause(t.getCause());
        }
        return msg;
    }

    /**
     * DOCUMENTME.
     *
     * @param   request             DOCUMENTME
     * @param   implementation      DOCUMENTME
     * @param   nodeName            DOCUMENTME
     * @param   requestIsAttribute  DOCUMENTME
     * @param   defaultValue        DOCUMENTME
     *
     * @return  DOCUMENTME
     */
    public static Boolean getBooleanParameter(int request, int implementation, String nodeName,
                                              boolean requestIsAttribute, Boolean defaultValue)
    {
        String value = getParameter(request, implementation, nodeName, requestIsAttribute, nodeName,
                                    null);

        if ((value == null) || (value.length() == 0))
        {
            return defaultValue;
        }

        if ("true".equals(value))
        {
            return true;
        }
        else if ("false".equals(value))
        {
            return false;
        }
        else
        {
            throw new IllegalArgumentException("Invalid value '" + value + "' for parameter: " +
                                               nodeName);
        }
    }

    /**
     * Returns the identifier as used in this connector.
     *
     * @param   uri  The uri to look for
     *
     * @return
     */
    public static String getDestinationIdentifier(String uri)
    {
        Destination dest = Destination.getDestination(uri);

        if (dest == null)
        {
            return null;
        }
        return dest.getDestinationManager().getName() + "." + dest.getName();
    }

    /**
     * Returns the internal (JMS) queue/topic identifier for a given destination, without any
     * parameters.
     *
     * @param   destination
     *
     * @return
     *
     * @throws  JMSException
     */
    public static String getDestinationURI(javax.jms.Destination destination)
                                    throws JMSException
    {
        String destURI = "";

        if (destination instanceof Queue)
        {
            destURI = ((Queue) destination).getQueueName();
        }
        else if (destination instanceof Topic)
        {
            destURI = ((Topic) destination).getTopicName();
        }

        if (destURI.indexOf("?") > -1) // remove all possible parameters...
        {
            destURI = destURI.substring(0, destURI.indexOf("?"));
        }

        return destURI;
    }

    /**
     * Returns the JMSConnector name as well as the provider specific destination URL for the given
     * JMS destination. The JMSConnector destination is a destination that is related to the given
     * JMS destination (e.g. the receiving destination).
     *
     * @param   dOrigConnectorDest  Related JMSConnector destination.
     * @param   dDest               JMS destination.
     * @param   bAddParameters      If <code>true</code> dynamic destinations parameters are added
     *                              to the physical name (provider URL).
     *
     * @return  String array where [0] = JMSConnector name, [1] = JMS provider URL (null if the
     *          destination is known).
     *
     * @throws  JMSException
     */
    public static String[] getJmsDestinationNames(Destination dOrigConnectorDest,
                                                  javax.jms.Destination dDest,
                                                  boolean bAddParameters)
                                           throws JMSException
    {
        String sProviderUrl = getDestinationURI(dDest);
        String sConnectorName = getDestinationIdentifier(sProviderUrl);

        if (sConnectorName != null)
        {
            return new String[] { sConnectorName, null };
        }

        // Unknown static destination. Just set it as a dynamic one.
        Destination dUseDest = null;

        if ((sProviderUrl != null) && (sProviderUrl.length() > 0))
        {
            // Destination physical name is known, so try to find a dynamic destination.
            dUseDest = dOrigConnectorDest.getDestinationManager().getDynamicDestination();
        }

        if (dUseDest == null)
        {
            // No dynamic destination configured or the physical name was not known, so just
            // use the default one. For triggers this is the destination to which the trigger is
            // attached to.
            dUseDest = dOrigConnectorDest;
        }

        sConnectorName = dUseDest.getDestinationManager().getName() + "." + dUseDest.getName();

        if ((sProviderUrl != null) && (sProviderUrl.length() > 0) && bAddParameters)
        {
            sProviderUrl = appendDynamicDestinationParameters(sProviderUrl, dUseDest,
                                                              dUseDest
                                                              .getDynamicDestinationParameterString());
        }

        return new String[]
               {
                   (sConnectorName != null) ? sConnectorName : "",
                   (sProviderUrl != null) ? sProviderUrl : ""
               };
    }

    /**
     * DOCUMENTME.
     *
     * @param   request             DOCUMENTME
     * @param   implementation      DOCUMENTME
     * @param   nodeName            DOCUMENTME
     * @param   requestIsAttribute  DOCUMENTME
     * @param   defaultValue        DOCUMENTME
     *
     * @return  DOCUMENTME
     */
    public static Long getLongParameter(int request, int implementation, String nodeName,
                                        boolean requestIsAttribute, Long defaultValue)
    {
        String value = getParameter(request, implementation, nodeName, requestIsAttribute, nodeName,
                                    null);

        if ((value == null) || (value.length() == 0))
        {
            return defaultValue;
        }

        try
        {
            return Long.parseLong(value);
        }
        catch (RuntimeException e)
        {
            throw new IllegalArgumentException("Invalid value '" + value + "' for parameter: " +
                                               nodeName);
        }
    }

    /**
     * DOCUMENTME.
     *
     * @param   request         DOCUMENTME
     * @param   implementation  DOCUMENTME
     * @param   nodeName        DOCUMENTME
     * @param   defaultValue    DOCUMENTME
     *
     * @return  DOCUMENTME
     */
    public static String getParameter(int request, int implementation, String nodeName,
                                      String defaultValue)
    {
        return getParameter(request, implementation, nodeName, false, nodeName, defaultValue);
    }

    /**
     * DOCUMENTME.
     *
     * @param   request             DOCUMENTME
     * @param   implementation      DOCUMENTME
     * @param   requestName         DOCUMENTME
     * @param   implementationName  DOCUMENTME
     * @param   defaultValue        DOCUMENTME
     *
     * @return  DOCUMENTME
     */
    public static String getParameter(int request, int implementation, String requestName,
                                      String implementationName, String defaultValue)
    {
        return getParameter(request, implementation, requestName, false, implementationName,
                            defaultValue);
    }

    /**
     * DOCUMENTME.
     *
     * @param   request             DOCUMENTME
     * @param   implementation      DOCUMENTME
     * @param   nodeName            DOCUMENTME
     * @param   requestIsAttribute  DOCUMENTME
     * @param   defaultValue        DOCUMENTME
     *
     * @return  DOCUMENTME
     */
    public static String getParameter(int request, int implementation, String nodeName,
                                      boolean requestIsAttribute, String defaultValue)
    {
        return getParameter(request, implementation, nodeName, requestIsAttribute, nodeName,
                            defaultValue);
    }

    /**
     * Returns a parameter value from the implementation or the request itself, based on the
     * implementation settings.
     *
     * @param   request             The request xml
     * @param   implementation      The implementation xml
     * @param   requestName         The parameter name in the request
     * @param   requestIsAttribute  true if the parameter is specified in an attribute in the
     *                              request
     * @param   implementationName  The parameter name in the implementation
     * @param   defaultValue        A default value
     *
     * @return  the value from the request if the implementation states that it was overridable,
     *          else the value from the implementation and if that one is not found either, the
     *          default value
     */
    public static String getParameter(int request, int implementation, String requestName,
                                      boolean requestIsAttribute, String implementationName,
                                      String defaultValue)
    {
        int impNode = Node.getElement(implementation, implementationName);

        if ((impNode == 0) || "true".equals(Node.getAttribute(impNode, "overridable")))
        {
            String sRes;

            if (requestIsAttribute)
            {
                sRes = Node.getAttribute(request, requestName, null);
            }
            else
            {
                sRes = Node.getDataElement(request, requestName, null);
            }

            if (sRes != null)
            {
                return sRes;
            }
        }
        return Node.getDataWithDefault(impNode, defaultValue);
    }

    /**
     * Returns a better stacktrace especially for JMSExceptions (contain a linked exception with
     * more exception details).
     *
     * @param   t  the exception to get it from
     *
     * @return  a string containing the stacktrace
     */
    public static String getStackTrace(Throwable t)
    {
        if (t == null)
        {
            return "";
        }

        StringBuffer sb = new StringBuffer();
        sb.append(t.getClass().getName());
        sb.append(": ");

        if (t.getMessage() != null)
        {
            sb.append(t.getMessage());
        }
        sb.append("\n");

        if (t instanceof JMSException)
        {
            sb.append("Caused by: ");
            sb.append(getStackTrace(((JMSException) t).getLinkedException()));
        }
        else if (t.getCause() != null)
        {
            sb.append("Caused by: ");
            sb.append(getStackTrace(t.getCause()));
        }
        else
        {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
            pw.flush();
            sb.append(sw.getBuffer());
        }
        return sb.toString();
    }

    /**
     * Get the properties from the given xml.
     *
     * @param   xml         the xml to read the properties from
     * @param   nodeName    the node name containing the property list
     * @param   properties  the object to put the properties into
     * @param   canChange   true if existing properties can be changed
     *
     * @throws  JMSConnectorException
     */
    public static void processProperties(int xml, String nodeName,
                                         Hashtable<String, Object> properties, boolean canChange)
                                  throws JMSConnectorException
    {
        int propNode = Node.getElement(xml, nodeName);

        if (propNode != 0)
        {
            for (int cnode = Node.getFirstChild(propNode); cnode != 0;
                     cnode = Node.getNextSibling(cnode))
            {
                String key = Node.getAttribute(cnode, "name");

                if (!canChange && properties.containsKey(key))
                {
                    continue;
                }

                if ((key == null) || (key.length() == 0))
                {
                    continue;
                }

                String type = Node.getAttribute(cnode, "type", null);

                if ((type == null) || (type.length() == 0))
                {
                    throw new JMSConnectorException("No type found for property '" + key + "'");
                }

                String value = Node.getData(cnode);
                Object objValue;

                if (type.equals("String"))
                {
                    objValue = value;
                }
                else if (type.equals("Short"))
                {
                    objValue = Short.valueOf(value);
                }
                else if (type.equals("Byte"))
                {
                    objValue = Byte.valueOf(value);
                }
                else if (type.equals("Boolean"))
                {
                    objValue = Boolean.valueOf(value);
                }
                else if (type.equals("Double"))
                {
                    objValue = Double.valueOf(value);
                }
                else if (type.equals("Float"))
                {
                    objValue = Float.valueOf(value);
                }
                else if (type.equals("Integer"))
                {
                    objValue = Integer.valueOf(value);
                }
                else if (type.equals("Long"))
                {
                    objValue = Long.valueOf(value);
                }
                else
                {
                    throw new JMSConnectorException("Unknown type '" + type + "' for property '" +
                                                    key + "'");
                }

                properties.put(key, objValue);
            }
        }
    }

    /**
     * Formats a a string so that it can be put into the log safely. Some characters seem to cause
     * problem.
     *
     * @param   oMessage  Object to be formatted. Uses toString().
     *
     * @return  Formattes string.
     */
    public static String safeFormatLogMessage(Object oMessage)
    {
        return safeFormatLogMessage(oMessage.toString());
    }

    /**
     * Formats a a string so that it can be put into the log safely. Some characters seem to cause
     * problem.
     *
     * @param   sMessage  Log message to be formatted.
     *
     * @return  Formattes string.
     */
    public static String safeFormatLogMessage(String sMessage)
    {
        StringBuilder sbRes = new StringBuilder(sMessage.length() + 20);
        int iLen = sMessage.length();

        for (int i = 0; i < iLen; i++)
        {
            char ch = sMessage.charAt(i);

            if ((ch < 9) || (ch == 11) || (ch == 12) || (ch > 127))
            {
                sbRes.append("[");
                sbRes.append((int) ch);
                sbRes.append("]");
            }
            else if ((ch == ']') && (i < (iLen - 2)) && (sMessage.charAt(i + 1) == ']') &&
                         (sMessage.charAt(i + 2) == '>'))
            {
                // Escape CDATA end marker.
                sbRes.append("]] >");
                i += 2;
            }
            else
            {
                sbRes.append(ch);
            }
        }

        return sbRes.toString();
    }

    /**
     * Creates a copy of the message, used to put a message into the error queue.
     *
     * @param   inputMsg  the message to copy
     * @param   session   the session to use for the new message
     *
     * @return  the copied message
     *
     * @throws  JMSException
     * @throws  JMSConnectorException
     */
    protected static Message getCopyOfMessageForSession(Message inputMsg, Session session)
                                                 throws JMSException, JMSConnectorException
    {
        Message outputMsg;

        if (inputMsg instanceof BytesMessage)
        {
            outputMsg = session.createBytesMessage();

            int len = (int) ((BytesMessage) inputMsg).getBodyLength();
            byte[] msg = new byte[len];
            ((BytesMessage) inputMsg).readBytes(msg);
            ((BytesMessage) outputMsg).writeBytes(msg);
        }
        else if (inputMsg instanceof TextMessage)
        {
            outputMsg = session.createTextMessage();
            ((TextMessage) outputMsg).setText(((TextMessage) inputMsg).getText());
        }
        else if (inputMsg instanceof MapMessage)
        {
            outputMsg = session.createMapMessage();

            Enumeration<?> mapNames = ((MapMessage) inputMsg).getMapNames();

            while (mapNames.hasMoreElements())
            {
                String key = (String) mapNames.nextElement();
                ((MapMessage) outputMsg).setObject(key, ((MapMessage) inputMsg).getObject(key));
            }
        }
        else if (inputMsg instanceof ObjectMessage)
        {
            outputMsg = session.createObjectMessage();
            ((ObjectMessage) outputMsg).setObject(((ObjectMessage) inputMsg).getObject());
        }
        else // StreamMessage is not implemented!!!
        {
            throw new JMSConnectorException("Unsupported message format: " +
                                            inputMsg.getClass().getName());
        }

        if (inputMsg.getJMSMessageID() != null)
        {
            outputMsg.setJMSMessageID(inputMsg.getJMSMessageID());
        }

        if (inputMsg.getJMSCorrelationID() != null)
        {
            outputMsg.setJMSCorrelationID(inputMsg.getJMSCorrelationID());
        }

        if (inputMsg.getJMSReplyTo() != null)
        {
            outputMsg.setJMSReplyTo(inputMsg.getJMSReplyTo());
        }

        if (inputMsg.getJMSType() != null)
        {
            outputMsg.setJMSType(inputMsg.getJMSType());
        }
        outputMsg.setJMSDeliveryMode(inputMsg.getJMSDeliveryMode());
        outputMsg.setJMSExpiration(inputMsg.getJMSExpiration());
        outputMsg.setJMSPriority(inputMsg.getJMSPriority());
        outputMsg.setJMSRedelivered(inputMsg.getJMSRedelivered());
        outputMsg.setJMSTimestamp(inputMsg.getJMSTimestamp());

        Enumeration<?> properties = inputMsg.getPropertyNames();

        while (properties.hasMoreElements())
        {
            String key = (String) properties.nextElement();

            if (key.startsWith("JMSX"))
            {
                continue;
            }
            outputMsg.setObjectProperty(key, inputMsg.getObjectProperty(key));
        }
        return outputMsg;
    }

    /**
     * Convert a message to XML.
     *
     * @param   dDest       Destination the this message comes from.
     * @param   msg         The message
     * @param   resultNode  the xml node containing the xml representation
     *
     * @throws  JMSException
     */
    protected static void getInformationFromMessage(Destination dDest, Message msg, int resultNode)
                                             throws JMSException
    {
        Node.createTextElement("messageid", msg.getJMSMessageID(), resultNode);

        if (msg.getJMSCorrelationID() != null)
        {
            Node.createTextElement("correlationid", msg.getJMSCorrelationID(), resultNode);
        }

        if (msg.getJMSType() != null)
        {
            Node.createTextElement("jmstype", msg.getJMSType(), resultNode);
        }

        if (msg.getJMSReplyTo() != null)
        {
            String[] saNames = getJmsDestinationNames(dDest, msg.getJMSReplyTo(), true);
            int xTmpNode;

            xTmpNode = Node.createTextElement("reply2destination", saNames[0], resultNode);

            if (saNames[1] != null)
            {
                // Unknown as a static destination. Just set it as a dynamic one.
                Node.setAttribute(xTmpNode, Destination.DESTINATION_PHYSICALNAME_ATTRIB,
                                  saNames[1]);
            }
        }

        // Set the from destination too.
        {
            String[] saNames = getJmsDestinationNames(dDest, msg.getJMSDestination(), true);
            int xTmpNode;

            xTmpNode = Node.createTextElement("fromdestination", saNames[0], resultNode);

            if (saNames[1] != null)
            {
                // Unknown as a static destination. Just set it as a dynamic one.
                Node.setAttribute(xTmpNode, Destination.DESTINATION_PHYSICALNAME_ATTRIB,
                                  saNames[1]);
            }
        }

        Enumeration<?> properties = msg.getPropertyNames();

        if (properties.hasMoreElements())
        {
            int propNode = Node.createElement("properties", resultNode);

            while (properties.hasMoreElements())
            {
                String key = (String) properties.nextElement();

                Object value = msg.getObjectProperty(key);
                int cnode = Node.createTextElement("property", String.valueOf(value), propNode);
                Node.setAttribute(cnode, "name", key);

                if (value instanceof String)
                {
                    Node.setAttribute(cnode, "type", "String");
                }
                else if (value instanceof Short)
                {
                    Node.setAttribute(cnode, "type", "Short");
                }
                else if (value instanceof Byte)
                {
                    Node.setAttribute(cnode, "type", "Byte");
                }
                else if (value instanceof Boolean)
                {
                    Node.setAttribute(cnode, "type", "Boolean");
                }
                else if (value instanceof Double)
                {
                    Node.setAttribute(cnode, "type", "Double");
                }
                else if (value instanceof Float)
                {
                    Node.setAttribute(cnode, "type", "Float");
                }
                else if (value instanceof Integer)
                {
                    Node.setAttribute(cnode, "type", "Integer");
                }
                else if (value instanceof Long)
                {
                    Node.setAttribute(cnode, "type", "Long");
                }
                else
                {
                    Node.setAttribute(cnode, "type", "Object");
                }
            }
        }
    }
}
