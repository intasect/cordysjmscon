/**
 * (c) 2006 Cordys R&D B.V. All rights reserved. The computer program(s) is the
 * proprietary information of Cordys B.V. and provided under the relevant
 * License Agreement containing restrictions on use and disclosure. Use is
 * subject to the License Agreement.
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
