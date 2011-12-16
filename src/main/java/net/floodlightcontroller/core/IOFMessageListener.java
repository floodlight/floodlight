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

package net.floodlightcontroller.core;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;

/**
 *
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public interface IOFMessageListener extends IListener<OFType> {
    public enum Command {
        CONTINUE, STOP
    }        
    
    public class FlListenerID {
        public static final int FL_FIRST_LISTENER_ID   = 0;
        public static final int OFMESSAGEFILTERMANAGER = FL_FIRST_LISTENER_ID;
        public static final int DEVICEMANAGERIMPL      = OFMESSAGEFILTERMANAGER+1;
        public static final int HUB                    = DEVICEMANAGERIMPL+1;
        public static final int LEARNINGSWITCH         = HUB+1;
        public static final int FORWARDINGBASE         = LEARNINGSWITCH+1;
        public static final int TOPOLOGYIMPL           = FORWARDINGBASE+1;
        public static final int FL_LAST_LISTENER_ID    = TOPOLOGYIMPL;
        
        public static String [] compName = new String[FL_LAST_LISTENER_ID+1];
                       
        public static void populateCompNames() {
            compName[OFMESSAGEFILTERMANAGER] = "OFMsg_Filter-Mgr";
            compName[DEVICEMANAGERIMPL]      = "Device-Manager";
            compName[HUB]                    = "Hub";
            compName[LEARNINGSWITCH]         = "Learning-Switch";
            compName[FORWARDINGBASE]         = "Forwarding-Base";
            compName[TOPOLOGYIMPL]           = "Topology";
        }
        
        public static String getListenerNameFromId(int id) {
            if ((id >= FL_FIRST_LISTENER_ID) && (id <= FL_LAST_LISTENER_ID)) {
                return compName[id];
            } else {
                return "Unknown-Component";
            }
        }
    }

  /**
   * This is the method Floodlight uses to call listeners with OpenFlow messages
   * @param sw the OpenFlow switch that sent this message
   * @param msg the message
   * @param cntx a floodlight message context object you can use to pass 
   * information between listeners
   * @return the command to continue or stop the execution
   */
  public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx);
}
