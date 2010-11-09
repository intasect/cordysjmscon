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