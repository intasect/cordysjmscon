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

/**
 * Poller to check the JMS connections as per the polling interval.
 */

public class JMSConnectionPoller implements Runnable
{
	private JMSConnector jmsconnector;
	private boolean stop = false;
	private long pollingIntervalInMS;
	
	public JMSConnectionPoller(JMSConnector jmsconnector, double pollingIntervalInMin)
	{
		this.jmsconnector = jmsconnector;
		if (pollingIntervalInMin < 0.166)
		{
            // Minimum interval is 10 seconds. 
			pollingIntervalInMin = 0.166;
		}
		this.pollingIntervalInMS = (long) (pollingIntervalInMin * 60 * 1000);
	}
	
	public void run()
	{
		while (!stop)
		{
			try {
				Thread.sleep(pollingIntervalInMS);
				jmsconnector.checkDestManagerConnections();
			} catch (InterruptedException e) {
				stop = true;
			}
		}
	}
}