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

/**
 * @author David Erickson (daviderickson@cs.stanford.edu) - Mar 11, 2010
 */
package org.openflow.protocol.action;


import org.jboss.netty.buffer.ChannelBuffer;


/**
 * Represents an ofp_action_strip_vlan
 * @author David Erickson (daviderickson@cs.stanford.edu) - Mar 11, 2010
 */
public class OFActionStripVirtualLan extends OFAction {
    public static int MINIMUM_LENGTH = 8;

    public OFActionStripVirtualLan() {
        super();
        super.setType(OFActionType.STRIP_VLAN);
        super.setLength((short) MINIMUM_LENGTH);
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);
        // PAD
        data.readInt();
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        // PAD
        data.writeInt(0);
    }
}