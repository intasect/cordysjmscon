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
package com.cordys.coe.ac.jmsconnector.ext;

/**
 * A static class for registering the provider specific extensions.
 *
 * @author  mpoyhone
 */
public enum EExtensionType
{
    /**
     * WebSphere MQ extension name for setting the message ID when sending a message to the queue.
     * This functionality is not allowed by the JMS standard.
     */
    WSMQ_SETMESSAGEID("com.cordys.coe.ac.jmsconnector.ext.webspheremq.MQSetOutgoingMessageId");

    /**
     * Implementing class.
     */
    private String sClassName;

    /**
     * Constructor for EExtensionType.
     *
     * @param  sClassName  Implementing class.
     */
    private EExtensionType(String sClassName)
    {
        this.sClassName = sClassName;
    }

    /**
     * Returns the implementation class.
     *
     * @return  The implementation class.
     */
    public String getClassName()
    {
        return sClassName;
    }
}
