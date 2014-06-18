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

package org.openflow.protocol;

import org.openflow.util.U16;

/**
 * Represents an OFPT_BARRIER_REQUEST message
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class OFBarrierRequest extends OFMessage {
    public OFBarrierRequest() {
        super();
        this.type = OFType.BARRIER_REQUEST;
        this.length = U16.t(OFMessage.MINIMUM_LENGTH);
    }
}
