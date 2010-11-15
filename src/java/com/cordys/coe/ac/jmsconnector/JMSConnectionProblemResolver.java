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

import com.eibus.management.IProblemResolver;
import com.eibus.management.IProblemStatusEventListener;

import com.eibus.util.logger.CordysLogger;

/**
 * Problem resolver for JMS connection problems.
 *
 * @author  mpoyhone
 */

public class JMSConnectionProblemResolver
    implements IProblemResolver
{
    /**
     * Contains the logger instance.
     */
    private static CordysLogger LOG = CordysLogger.getCordysLogger(JMSConnectionProblemResolver.class);
    /**
     * JMS connector object.
     */
    private JMSConnector jcConnector;
    /**
     * Initial reconnect try interval. Needs to be longer if the MQ is shutting down.
     */
    public static final long INITIAL_POLL_INTERVAL = 60000L;
    /**
     * Reconnect try interval.
     */
    public static final long POLL_INTERVAL = 30000L;

    /**
     * Constructor for JMSConnectionProblemResolver.
     *
     * @param  jcConnector  JMS connector object.
     */
    public JMSConnectionProblemResolver(JMSConnector jcConnector)
    {
        this.jcConnector = jcConnector;
    }

    /**
     * @see  com.eibus.management.IProblemResolver#resolveProblem(com.eibus.management.IProblemStatusEventListener)
     */
    public void resolveProblem(IProblemStatusEventListener pselListener)
    {
        try
        {
            if (LOG.isDebugEnabled())
            {
                LOG.debug("Trying to re-open the JMS connection.");
            }

            jcConnector.openJmsConnection();

            if (LOG.isDebugEnabled())
            {
                LOG.debug("JMS connection has been succesfully re-opened.");
            }

            // Connection is back again.
            pselListener.notifyResolved();
        }
        catch (Exception e)
        {
            if (LOG.isDebugEnabled())
            {
                LOG.debug("Re-opening failed.", e);
            }

            pselListener.notifyNotResolved(POLL_INTERVAL);
        }
    }
}
