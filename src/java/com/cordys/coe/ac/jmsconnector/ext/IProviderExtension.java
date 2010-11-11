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
package com.cordys.coe.ac.jmsconnector.ext;

import javax.jms.Message;

import com.cordys.coe.ac.jmsconnector.Destination;
import com.cordys.coe.ac.jmsconnector.JMSConnector;

/**
 * An interface for extending provider the connector to support
 * provider specific extensions.
 *
 * @author mpoyhone
 */
public interface IProviderExtension
{
    /**
     * Called when the extension is being initialize.
     * @param jcConnector JMSConnector has the holds the extension.
     * @return <code>true</code> if this extension should be enabled.
     */
    boolean initialize(JMSConnector jcConnector);

    /**
     * Called before a message is being sent.
     * @param dDest JMS connector destination to which the message is being sent.
     * @param mMsg Message to be sent.
     */
    void onBeforeSendMessage(Destination dDest, Message mMsg);
}
