/**
 * (c) 2009 Cordys R&D B.V. All rights reserved. The computer program(s) is the
 * proprietary information of Cordys B.V. and provided under the relevant
 * License Agreement containing restrictions on use and disclosure. Use is
 * subject to the License Agreement.
 */
package com.cordys.coe.ac.jmsconnector;

import junit.framework.TestCase;

/**
 * Test cases for JMSUtil class.
 *
 * @author mpoyhone
 */
public class JMSUtilTest extends TestCase
{

    /**
     * Set up
     * @throws java.lang.Exception Not thrown
     */
    @Override
    public void setUp() throws Exception
    {
    }

    /**
     * Tear down
     * @throws java.lang.Exception Not thrown
     */
    @Override
    public void tearDown() throws Exception
    {
    }

    /**
     * Test method for {@link com.cordys.coe.ac.jmsconnector.JMSUtil#safeFormatLogMessage(java.lang.String)}.
     */
    public void testSafeFormatLogMessageString_CDATA()
    {
        String xmlWithNestedCDATA = "<a><![CDATA[<![CDATA[blah]]> more blah]]></a>";
        String expected           = "<a><![CDATA[<![CDATA[blah]] > more blah]] ></a>";
        
        assertEquals(expected, JMSUtil.safeFormatLogMessage(xmlWithNestedCDATA));
    }

}
