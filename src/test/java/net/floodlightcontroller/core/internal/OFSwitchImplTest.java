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

import net.floodlightcontroller.core.IFloodlightProviderService.Role;
import net.floodlightcontroller.test.FloodlightTestCase;

import org.junit.Before;
import org.junit.Test;

public class OFSwitchImplTest extends FloodlightTestCase {
    protected OFSwitchImpl sw;
    
    
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        sw = new OFSwitchImpl();
    }    
    
    @Test
    public void testSetHARoleReply() {

        sw.setHARole(Role.MASTER);
        assertEquals(Role.MASTER, sw.getHARole());
        
        sw.setHARole(Role.EQUAL);
        assertEquals(Role.EQUAL, sw.getHARole());
        
        sw.setHARole(Role.SLAVE);
        assertEquals(Role.SLAVE, sw.getHARole());
    }
    
}
