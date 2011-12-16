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

package org.openflow.protocol.statistics;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * The base class for all OpenFlow statistics.
 *
 * @author David Erickson (daviderickson@cs.stanford.edu) - Mar 11, 2010
 */
public interface OFStatistics {
    /**
     * Returns the wire length of this message in bytes
     * @return the length
     */
    public int getLength();

    /**
     * Read this message off the wire from the specified ByteBuffer
     * @param data
     */
    public void readFrom(ChannelBuffer data);

    /**
     * Write this message's binary format to the specified ByteBuffer
     * @param data
     */
    public void writeTo(ChannelBuffer data);
}
