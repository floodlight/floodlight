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

package org.openflow.protocol.factory;

import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionType;


/**
 * The interface to factories used for retrieving OFAction instances. All
 * methods are expected to be thread-safe.
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public interface OFActionFactory {
    /**
     * Retrieves an OFAction instance corresponding to the specified
     * OFActionType
     * @param t the type of the OFAction to be retrieved
     * @return an OFAction instance
     */
    public OFAction getAction(OFActionType t);

    /**
     * Attempts to parse and return all OFActions contained in the given
     * ByteBuffer, beginning at the ByteBuffer's position, and ending at
     * position+length.
     * @param data the ChannelBuffer to parse for OpenFlow actions
     * @param length the number of Bytes to examine for OpenFlow actions
     * @return a list of OFAction instances
     */
    public List<OFAction> parseActions(ChannelBuffer data, int length);

    /**
     * Attempts to parse and return all OFActions contained in the given
     * ByteBuffer, beginning at the ByteBuffer's position, and ending at
     * position+length.
     * @param data the ChannelBuffer to parse for OpenFlow actions
     * @param length the number of Bytes to examine for OpenFlow actions
     * @param limit the maximum number of messages to return, 0 means no limit
     * @return a list of OFAction instances
     */
    public List<OFAction> parseActions(ChannelBuffer data, int length, int limit);
}
