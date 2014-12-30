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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;
import net.floodlightcontroller.core.IOFSwitchBackend;
import net.floodlightcontroller.core.OFSwitch;
import net.floodlightcontroller.core.SwitchDriverSubHandshakeAlreadyStarted;
import net.floodlightcontroller.core.SwitchDriverSubHandshakeCompleted;
import net.floodlightcontroller.core.SwitchDriverSubHandshakeNotStarted;
import net.floodlightcontroller.core.util.URIUtil;
import org.projectfloodlight.openflow.protocol.OFBsnControllerConnection;
import org.projectfloodlight.openflow.protocol.OFBsnControllerConnectionState;
import org.projectfloodlight.openflow.protocol.OFBsnControllerConnectionsReply;
import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFNiciraControllerRole;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFAuxId;

public class OFSwitchTest {
    protected OFSwitch sw;
    protected OFFactory factory = OFFactories.getFactory(OFVersion.OF_13);

    @Before
    public void setUp() throws Exception {
        MockOFConnection mockConnection = new MockOFConnection(DatapathId.of(1), OFAuxId.MAIN);
        sw = new OFSwitch(mockConnection, OFFactories.getFactory(OFVersion.OF_10),
                EasyMock.createMock(IOFSwitchManager.class), DatapathId.of(1));
    }

    @Test
    public void testSetHARoleReply() {
        sw.setControllerRole(OFControllerRole.ROLE_MASTER);
        assertEquals(OFControllerRole.ROLE_MASTER, sw.getControllerRole());

        sw.setControllerRole(OFControllerRole.ROLE_EQUAL);
        assertEquals(OFControllerRole.ROLE_EQUAL, sw.getControllerRole());

        sw.setControllerRole(OFControllerRole.ROLE_SLAVE);
        assertEquals(OFControllerRole.ROLE_SLAVE, sw.getControllerRole());
    }

    @Test
    public void testSubHandshake() {
        OFFactory factory = OFFactories.getFactory(OFVersion.OF_10);
        OFMessage m = factory.buildNiciraControllerRoleReply()
                .setXid(1)
                .setRole(OFNiciraControllerRole.ROLE_MASTER)
                .build();
        // test exceptions before handshake is started
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

    /**
     * Helper to load controller connection messages into a switch for testing.
     * @param sw the switch to insert the message on
     * @param role the role for the controller connection message
     * @param state the state for the controller connection message
     * @param uri the URI for the controller connection message
     */
    public void updateControllerConnections(IOFSwitchBackend sw, OFControllerRole role1, OFBsnControllerConnectionState state1, String uri1
                                            ,  OFControllerRole role2, OFBsnControllerConnectionState state2, String uri2) {
        OFBsnControllerConnection connection1 = factory.buildBsnControllerConnection()
                .setAuxiliaryId(OFAuxId.MAIN)
                .setRole(role1)
                .setState(state1)
                .setUri(uri1)
                .build();

        OFBsnControllerConnection connection2 = factory.buildBsnControllerConnection()
                .setAuxiliaryId(OFAuxId.MAIN)
                .setRole(role2)
                .setState(state2)
                .setUri(uri2)
                .build();

        List<OFBsnControllerConnection> connections = new ArrayList<OFBsnControllerConnection>();
        connections.add(connection1);
        connections.add(connection2);

        OFBsnControllerConnectionsReply reply = factory.buildBsnControllerConnectionsReply()
                .setConnections(connections)
                .build();

        sw.updateControllerConnections(reply);
    }

    /**
     * This test ensures that the switch accurately determined if another master
     * exists in the cluster by examining the controller connections it has.
     */
    @Test
    public void testHasAnotherMaster() {
        URI cokeUri = URIUtil.createURI("1.2.3.4", 6653);
        InetSocketAddress address = (InetSocketAddress) sw.getConnection(OFAuxId.MAIN).getLocalInetAddress();
        URI pepsiUri = URIUtil.createURI(address.getHostName(), address.getPort());

        updateControllerConnections(sw, OFControllerRole.ROLE_SLAVE, OFBsnControllerConnectionState.BSN_CONTROLLER_CONNECTION_STATE_CONNECTED, cokeUri.toString(),
                                    OFControllerRole.ROLE_MASTER, OFBsnControllerConnectionState.BSN_CONTROLLER_CONNECTION_STATE_CONNECTED, pepsiUri.toString());

        // From the perspective of pepsi, the cluster currently does NOT have another master controller
        assertFalse(sw.hasAnotherMaster());

        // Switch the controller connections so that pepsi is no longer master
        updateControllerConnections(sw, OFControllerRole.ROLE_MASTER, OFBsnControllerConnectionState.BSN_CONTROLLER_CONNECTION_STATE_CONNECTED, cokeUri.toString(),
                                    OFControllerRole.ROLE_SLAVE, OFBsnControllerConnectionState.BSN_CONTROLLER_CONNECTION_STATE_CONNECTED, pepsiUri.toString());

        // From the perspective of pepsi, the cluster currently has another master controller
        assertTrue(sw.hasAnotherMaster());
    }
}
