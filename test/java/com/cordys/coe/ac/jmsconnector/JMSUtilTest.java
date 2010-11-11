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
