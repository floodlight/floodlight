/**
 *    Copyright 2013, Big Switch Networks, Inc.
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

package net.floodlightcontroller.core.internal;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.test.FloodlightTestCase;

import org.junit.Before;
import org.junit.Test;

public class OFSwitchImplTest extends FloodlightTestCase {
    protected OFSwitchImpl sw;
    
    
    @Before
    public void setUp() throws Exception {
        super.setUp();
        sw = new OFSwitchImpl();
    }    
    
    @Test
    public void testSetHARoleReplyReceived() {
        assertEquals(null, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));

        sw.setHARole(Role.MASTER, true);
        assertEquals(Role.MASTER, sw.getHARole());
        assertEquals(true, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        
        sw.setHARole(Role.EQUAL, true);
        assertEquals(Role.EQUAL, sw.getHARole());
        assertEquals(true, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        
        sw.setHARole(Role.SLAVE, true);
        assertEquals(Role.SLAVE, sw.getHARole());
        assertEquals(true, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
    }
    
    @Test
    public void testSetHARoleNoReply() {
        assertEquals(null, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));

        sw.setHARole(Role.MASTER, false);
        assertEquals(Role.MASTER, sw.getHARole());
        assertEquals(false, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        
        sw.setHARole(Role.EQUAL, false);
        assertEquals(Role.EQUAL, sw.getHARole());
        assertEquals(false, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
        
        sw.setHARole(Role.SLAVE, false);
        assertEquals(Role.SLAVE, sw.getHARole());
        assertEquals(false, sw.getAttribute(IOFSwitch.SWITCH_SUPPORTS_NX_ROLE));
    }

}
