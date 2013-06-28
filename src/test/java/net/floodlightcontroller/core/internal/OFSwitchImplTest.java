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

import net.floodlightcontroller.core.SwitchDriverSubHandshakeAlreadyStarted;
import net.floodlightcontroller.core.SwitchDriverSubHandshakeCompleted;
import net.floodlightcontroller.core.SwitchDriverSubHandshakeNotStarted;
import net.floodlightcontroller.core.IFloodlightProviderService.Role;

import org.junit.Before;
import org.junit.Test;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.openflow.protocol.factory.BasicFactory;

import static org.junit.Assert.*;

public class OFSwitchImplTest {
    protected OFSwitchImpl sw;


    @Before
    public void setUp() throws Exception {
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

    @Test
    public void testSubHandshake() {
        OFMessage m = BasicFactory.getInstance().getMessage(OFType.VENDOR);
        // test execptions before handshake is started
        try {
            sw.processDriverHandshakeMessage(m);
            fail("expected exception not thrown");
        } catch (SwitchDriverSubHandshakeNotStarted e) { /* expected */ }
        try {
            sw.isDriverHandshakeComplete();
            fail("expected exception not thrown");
        } catch (SwitchDriverSubHandshakeNotStarted e) { /* expected */ }

        // start the handshake -- it should immediately complete
        sw.startDriverHandshake();
        assertTrue("Handshake should be complete",
                   sw.isDriverHandshakeComplete());

        // test exceptions after handshake is completed
        try {
            sw.processDriverHandshakeMessage(m);
            fail("expected exception not thrown");
        } catch (SwitchDriverSubHandshakeCompleted e) { /* expected */ }
        try {
            sw.startDriverHandshake();
            fail("Expected exception not thrown");
        } catch (SwitchDriverSubHandshakeAlreadyStarted e) { /* expected */ }
    }
}
