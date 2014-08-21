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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

import net.floodlightcontroller.core.HARole;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.util.FilterIterator;
import net.floodlightcontroller.core.web.serializers.IOFSwitchSerializer;
import org.projectfloodlight.openflow.protocol.OFCapabilities;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.protocol.OFActionType;
import org.restlet.data.Form;
import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
/**
 * Get a list of switches connected to the controller
 * @author readams
 */
public class ControllerSwitchesResource extends ServerResource {
    
    public static final String DPID_ERROR =
            "Invalid Switch DPID: must be a 64-bit quantity, expressed in " +
            "hex as AA:BB:CC:DD:EE:FF:00:11";

    @Get("json")
    public Collection<IOFSwitch> retrieve(){
        IOFSwitchService switchService = 
            (IOFSwitchService) getContext().getAttributes().
                get(IOFSwitchService.class.getCanonicalName());
        DatapathId switchDPID = null;
        ArrayList<IOFSwitch> result = new ArrayList<IOFSwitch>();
        Form form = getQuery();
        String dpid = form.getFirstValue("dpid", true);
        if( dpid != null){
            try{
                switchDPID = DatapathId.of(dpid);
            }catch(Exception e){
                setStatus(Status.CLIENT_ERROR_BAD_REQUEST,DPID_ERROR);
                return null;
            }
        }
        if (switchDPID != null){
            IOFSwitch sw = switchService.getSwitch(switchDPID);
            if ( sw != null){
                result.add(sw);
            }
            return result;
        }
        final String dpidStartsWith = 
            form.getFirstValue("dpid__startswith",true);
        if(dpidStartsWith != null){
            for(IOFSwitch sw: switchService.getAllSwitchMap().values()){
                if( sw.getId().toString().startsWith(dpidStartsWith))
                        result.add(sw);
            }
            return result;
        }
        return switchService.getAllSwitchMap().values();

    
    }
}
