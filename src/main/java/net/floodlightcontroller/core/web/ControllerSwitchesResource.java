/**
*    Copyright 2011, Big Switch Networks, Inc.
*    Originally created by David Erickson, Stanford University
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

package net.floodlightcontroller.core.web;

import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Collection;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.util.FilterIterator;

import org.openflow.protocol.OFPhysicalPort;
import org.openflow.util.HexString;
import org.restlet.data.Form;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

/**
 * Get a list of switches connected to the controller
 * @author readams
 */
public class ControllerSwitchesResource extends ServerResource {
    /**
     * A wrapper class around IOFSwitch that defines the REST serialization
     * fields. Could have written a Jackson serializer but this is easier.
     */
    public static class SwitchJsonSerializerWrapper {
        private final IOFSwitch sw;

        public SwitchJsonSerializerWrapper(IOFSwitch sw) {
            this.sw = sw;
        }

        public int getActions() {
            return sw.getActions();
        }

        public Map<Object, Object> getAttributes() {
            return sw.getAttributes();
        }

        public Map<String,String> getDescription() {
            Map<String,String> rv = new HashMap<String, String>();
            if (sw.getDescriptionStatistics() == null) {
                rv.put("manufacturer", "");
                rv.put("hardware", "");
                rv.put("software", "");
                rv.put("serialNum", "");
                rv.put("datapath", "");
            } else {
                rv.put("manufacturer",
                       sw.getDescriptionStatistics().getManufacturerDescription());
                rv.put("hardware",
                       sw.getDescriptionStatistics().getHardwareDescription());
                rv.put("software",
                       sw.getDescriptionStatistics().getSoftwareDescription());
                rv.put("serialNum",
                       sw.getDescriptionStatistics().getSerialNumber());
                rv.put("datapath",
                       sw.getDescriptionStatistics().getDatapathDescription());
            }
            return rv;
        }

        public int getBuffers() {
            return sw.getBuffers();
        }

        public int getCapabilities() {
            return sw.getCapabilities();
        }

        public long getConnectedSince() {
            if (sw.getConnectedSince() == null)
                return 0;
            return sw.getConnectedSince().getTime();
        }

        public String getDpid() {
            return sw.getStringId();
        }

        public String getHarole() {
            if (sw.getHARole() == null)
                return "null";
            return sw.getHARole().toString();
        }

        public String getInetAddress() {
            SocketAddress addr = sw.getInetAddress();
            if (addr == null)
                return null;
            return addr.toString();
        }

        public Collection<OFPhysicalPort> getPorts() {
            return ImmutablePort.ofPhysicalPortListOf(sw.getPorts());
        }
    }

    /**
     * Wraps an iterator of IOFSwitch into an iterator of
     * SwitchJsonSerializerWrapper
     * @author gregor
     */
    private static class SwitchJsonSerializerWrapperIterator
            implements Iterator<SwitchJsonSerializerWrapper> {
        private final Iterator<IOFSwitch> iter;

        public SwitchJsonSerializerWrapperIterator(Iterator<IOFSwitch> iter) {
            this.iter = iter;
        }

        @Override
        public boolean hasNext() {
            return this.iter.hasNext();
        }

        @Override
        public SwitchJsonSerializerWrapper next() {
            return new SwitchJsonSerializerWrapper(iter.next());
        }

        @Override
        public void remove() {
            this.iter.remove();
        }
    }

    public static final String DPID_ERROR =
            "Invalid Switch DPID: must be a 64-bit quantity, expressed in " +
            "hex as AA:BB:CC:DD:EE:FF:00:11";

    @Get("json")
    public Iterator<SwitchJsonSerializerWrapper> retrieve() {
        IFloodlightProviderService floodlightProvider =
                (IFloodlightProviderService)getContext().getAttributes().
                    get(IFloodlightProviderService.class.getCanonicalName());

        Long switchDPID = null;

        Form form = getQuery();
        String dpid = form.getFirstValue("dpid", true);
        if (dpid != null) {
            try {
                switchDPID = HexString.toLong(dpid);
            } catch (Exception e) {
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST, DPID_ERROR);
                return null;
            }
        }
        if (switchDPID != null) {
            IOFSwitch sw =
                    floodlightProvider.getSwitch(switchDPID);
            if (sw != null) {
                SwitchJsonSerializerWrapper wrappedSw =
                        new SwitchJsonSerializerWrapper(sw);
                return Collections.singleton(wrappedSw).iterator();
            }
            return Collections.<SwitchJsonSerializerWrapper>emptySet().iterator();
        }
        final String dpidStartsWith =
                form.getFirstValue("dpid__startswith", true);

        Iterator<IOFSwitch> iofSwitchIter =
                floodlightProvider.getAllSwitchMap().values().iterator();
        Iterator<SwitchJsonSerializerWrapper> switer =
                new SwitchJsonSerializerWrapperIterator(iofSwitchIter);
        if (dpidStartsWith != null) {
            return new FilterIterator<SwitchJsonSerializerWrapper>(switer) {
                @Override
                protected boolean matches(SwitchJsonSerializerWrapper value) {
                    return value.getDpid().startsWith(dpidStartsWith);
                }
            };
        }
        return switer;
    }
}
