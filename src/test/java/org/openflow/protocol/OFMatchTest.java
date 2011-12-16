/**
*    Copyright (c) 2008 The Board of Trustees of The Leland Stanford Junior
*    University
* 
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

package org.openflow.protocol;

import junit.framework.TestCase;

public class OFMatchTest extends TestCase {
    public void testFromString() {
        OFMatch correct = new OFMatch();
        OFMatch tester = new OFMatch();

        // Various combinations of "all"/"any"
        tester.fromString("OFMatch[]");
        // correct is already wildcarded
        TestCase.assertEquals(correct, tester);
        tester.fromString("all");
        TestCase.assertEquals(correct, tester);
        tester.fromString("ANY");
        TestCase.assertEquals(correct, tester);
        tester.fromString("");
        TestCase.assertEquals(correct, tester);
        tester.fromString("[]");
        TestCase.assertEquals(correct, tester);

        // ip_src
        correct.setWildcards(~OFMatch.OFPFW_NW_SRC_MASK);
        correct.setNetworkSource(0x01010203);
        tester.fromString("nw_src=1.1.2.3");
        TestCase.assertEquals(correct.getNetworkSourceMaskLen(), tester
                .getNetworkSourceMaskLen());
        TestCase.assertEquals(correct, tester);
        tester.fromString("IP_sRc=1.1.2.3");
        TestCase.assertEquals(correct.getNetworkSourceMaskLen(), tester
                .getNetworkSourceMaskLen());
        TestCase.assertEquals(correct, tester);
    }

    public void testToString() {
        OFMatch match = new OFMatch();
        match.fromString("nw_dst=3.4.5.6/8");
        TestCase.assertEquals(8, match.getNetworkDestinationMaskLen());
        String correct = "OFMatch[nw_dst=3.0.0.0/8]";
        String tester = match.toString();

        TestCase.assertEquals(correct, tester);
        tester = "OFMatch[dl_type=35020]";
        correct = "OFMatch[dl_type=0x88cc]";
        match = new OFMatch();
        match.fromString(tester);
        TestCase.assertEquals(correct, match.toString());
        OFMatch match2 = new OFMatch();
        match2.fromString(correct);
        TestCase.assertEquals(match, match2);
    }

    public void testClone() {
        OFMatch match1 = new OFMatch();
        OFMatch match2 = match1.clone();
        TestCase.assertEquals(match1, match2);
        match2.setNetworkProtocol((byte) 4);
        match2.setWildcards(match2.getWildcards() & ~OFMatch.OFPFW_NW_PROTO);
        TestCase.assertNotSame(match1, match2);
    }

    public void testIpToString() {
        String test = OFMatch.ipToString(-1);
        TestCase.assertEquals("255.255.255.255", test);
    }
}
