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

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;


/**
 * The interface to factories used for retrieving OFMessage instances. All
 * methods are expected to be thread-safe.
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public interface OFMessageFactory {
    /**
     * Retrieves an OFMessage instance corresponding to the specified OFType
     * @param t the type of the OFMessage to be retrieved
     * @return an OFMessage instance
     */
    public OFMessage getMessage(OFType t);

    /**
     * Attempts to parse and return a OFMessages contained in the given
     * ChannelBuffer, beginning at the ChannelBuffer's position, and ending at the
     * after the first parsed message
     * @param data the ChannelBuffer to parse for an OpenFlow message
     * @return a list of OFMessage instances
     * @throws MessageParseException 
     */
    public OFMessage parseMessage(ChannelBuffer data) throws MessageParseException;

    /**
     * Retrieves an OFActionFactory
     * @return an OFActionFactory
     */
    public OFActionFactory getActionFactory();
}
