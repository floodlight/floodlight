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

package net.floodlightcontroller.util;

import static org.junit.Assert.*;

import java.net.SocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.core.IOFConnection;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.LogicalOFMessageCategory;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.internal.OFConnection;
import net.floodlightcontroller.core.internal.TableFeatures;

import org.projectfloodlight.openflow.protocol.OFActionType;
import org.projectfloodlight.openflow.protocol.OFCapabilities;
import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFRequest;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFStatsRequest;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;


/**
 * A mock implementation of IFOSwitch we use for {@link OFMessageDamper}
 *
 * We need to mock equals() and hashCode() but alas, EasyMock doesn't support
 * this. Sigh. And of course this happens to be the interface with the most
 * methods.
 * @author gregor
 *
 */
public class OFMessageDamperMockSwitch implements IOFSwitch {
    OFMessage writtenMessage;

    public OFMessageDamperMockSwitch() {
        reset();
    }

    /* reset this mock. I.e., clear the stored message previously written */
    public void reset() {
        writtenMessage = null;
    }

    /* assert that a message was written to this switch and that the
     * written message and context matches the expected values
     * @param expected
     */
    public void assertMessageWasWritten(OFMessage expected) {
        assertNotNull("No OFMessage was written", writtenMessage);
        assertEquals(expected, writtenMessage);
    }

    /*
     * assert that no message was written
     */
    public void assertNoMessageWritten() {
        assertNull("OFMessage was written but didn't expect one",
                      writtenMessage);
    }

    /*
     * use hashCode() and equals() from Object
     */


    //-------------------------------------------------------
    // IOFSwitch: mocked methods


    //-------------------------------------------------------
    // IOFSwitch: not-implemented methods


    @Override
    public boolean portEnabled(String portName) {
        assertTrue("Unexpected method call", false);
        return false;
    }

    @Override
    public DatapathId getId() {
        assertTrue("Unexpected method call", false);
        return DatapathId.NONE;
    }

    @Override
    public SocketAddress getInetAddress() {
        assertTrue("Unexpected method call", false);
        return null;
    }

    @Override
    public Map<Object, Object> getAttributes() {
        assertTrue("Unexpected method call", false);
        return null;
    }

    @Override
    public Date getConnectedSince() {
        assertTrue("Unexpected method call", false);
        return null;
    }

    @Override
    public boolean isConnected() {
        assertTrue("Unexpected method call", false);
        return false;
    }

    @Override
    public boolean hasAttribute(String name) {
        assertTrue("Unexpected method call", false);
        return false;
    }

    @Override
    public Object getAttribute(String name) {
        assertTrue("Unexpected method call", false);
        return null;
    }

    @Override
    public void setAttribute(String name, Object value) {
        assertTrue("Unexpected method call", false);
    }

    @Override
    public Object removeAttribute(String name) {
        assertTrue("Unexpected method call", false);
        return null;
    }

    @Override
    public long getBuffers() {
        fail("Unexpected method call");
        return 0;
    }

    @Override
    public Set<OFActionType> getActions() {
        fail("Unexpected method call");
        return null;
    }

    @Override
    public Set<OFCapabilities> getCapabilities() {
        fail("Unexpected method call");
        return null;
    }

    @Override
    public short getNumTables() {
        fail("Unexpected method call");
        return 0;
    }
    
    @Override
    public Collection<TableId> getTables() {
    	fail("Unexpected method call");
    	return null;
    }

    @Override
    public boolean attributeEquals(String name, Object other) {
        fail("Unexpected method call");
        return false;
    }

    @Override
    public boolean isActive() {
        fail("Unexpected method call");
        return false; // never reached
    }

	@Override
	public boolean write(OFMessage m) {
		writtenMessage = m;
		return true;
	}

	@Override
	public Collection<OFMessage> write(Iterable<OFMessage> msgList) {
		return Collections.emptyList();
	}

	@Override
	public <R extends OFMessage> ListenableFuture<R> writeRequest(
			OFRequest<R> request) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <REPLY extends OFStatsReply> ListenableFuture<List<REPLY>> writeStatsRequest(
			OFStatsRequest<REPLY> request) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SwitchStatus getStatus() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void disconnect() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public SwitchDescription getSwitchDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public OFPortDesc getPort(OFPort portNumber) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<OFPortDesc> getSortedPorts() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean portEnabled(OFPort portNumber) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public OFControllerRole getControllerRole() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public OFFactory getOFFactory() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ImmutableList<IOFConnection> getConnections() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean write(OFMessage m, LogicalOFMessageCategory category) {
		return true;
	}

	@Override
	public Collection<OFMessage> write(Iterable<OFMessage> msgList,
			LogicalOFMessageCategory category) {
		return Collections.emptyList();
	}

	@Override
	public OFConnection getConnectionByCategory(
			LogicalOFMessageCategory category) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <REPLY extends OFStatsReply> ListenableFuture<List<REPLY>> writeStatsRequest(
			OFStatsRequest<REPLY> request, LogicalOFMessageCategory category) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <R extends OFMessage> ListenableFuture<R> writeRequest(
			OFRequest<R> request, LogicalOFMessageCategory category) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<OFPortDesc> getEnabledPorts() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<OFPort> getEnabledPortNumbers() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public OFPortDesc getPort(String portName) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<OFPortDesc> getPorts() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public TableFeatures getTableFeatures(TableId table) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public U64 getLatency() {
		// TODO Auto-generated method stub
		return null;
	}
}
