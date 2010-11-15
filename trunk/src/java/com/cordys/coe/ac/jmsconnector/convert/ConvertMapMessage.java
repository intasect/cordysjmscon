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
import com.eibus.xml.nom.Node;

import java.util.Enumeration;

import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.TextMessage;

/**
 * Class implements the conversion of a MapMessage into a TextMessage. All the message properties
 * are being populated into the following XML format:
 *
 * <p>&gt;Properties&lt; &gt;Property&lt; &gt;Key /&lt; &gt;Value /&lt; &gt;/Property&lt;
 * &gt;Property&lt; &gt;Key /&lt; &gt;Value /&lt; &gt;/Property&lt; &gt;/Properties&lt;</p>
 *
 * <p>Note: Please bare in mind that the data type is lost during the conversion.</p>
 *
 * @author  fvdwende
 */
public class ConvertMapMessage extends MessageConverter
{
    /**
     * Definition of the tag names in the XML result.
     */
    protected static final String TAG_PROPERTIES = "Properties";
    /**
     * DOCUMENTME.
     */
    protected static final String TAG_PROPERTY = "Property";
    /**
     * DOCUMENTME.
     */
    protected static final String TAG_KEY = "Key";
    /**
     * DOCUMENTME.
     */
    protected static final String TAG_VALUE = "Value";

    /**
     * Parameterized constructor which sets the session and message.
     *
     * @param   session  The JMS session object
     * @param   message  The JMS message object
     *
     * @throws  JMSConnectorException  DOCUMENTME
     */
    public ConvertMapMessage(Session session, Message message)
                      throws JMSConnectorException
    {
        super(session, message);
    }

    /*
     * (non-Javadoc)
     * @see com.cordys.coe.ac.jmsconnector.convert.MessageConverter#convert()
     */
    @Override public TextMessage convert(Document doc)
                                  throws JMSException, JMSConnectorException
    {
        TextMessage result = null;
        int node = 0;

        try
        {
            if (message instanceof MapMessage)
            {
                // Read the map message and create a NOM node
                Enumeration<?> e = ((MapMessage) message).getMapNames();
                node = doc.createElement(TAG_PROPERTIES);

                while (e.hasMoreElements())
                {
                    String key = (String) e.nextElement();
                    // Not sure how to retrieve the type of a property so
                    // simply handling all properties as String
                    String value = ((MapMessage) message).getString(key);
                    addProperty(node, key, value);
                }
                // Now create the text message
                result = session.createTextMessage(Node.writeToString(node, true));
            }
            else
            {
                throw new JMSConnectorException("This converter can only handle MapMessages.");
            }
        }
        finally
        {
            if (node != 0)
            {
                Node.delete(node);
                node = 0;
            }
        }
        return result;
    }

    /**
     * Method creates a new NOM element with the giver property and key.
     *
     * @param  parent  The NOM element acting as a parent for the node being created
     * @param  key     The key of the message property
     * @param  value   The keys value of the message property
     */
    protected static void addProperty(int parent, String key, String value)
    {
        int propertyNode = Node.createElement(TAG_PROPERTY, parent);
        Node.createTextElement(TAG_KEY, key, propertyNode);
        Node.createTextElement(TAG_VALUE, value, propertyNode);
    }
}
