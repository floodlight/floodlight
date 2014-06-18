/**
*    Copyright 2012, Andrew Ferguson, Brown University
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

package org.openflow.vendor.openflow;

import org.openflow.protocol.Instantiable;
import org.openflow.protocol.vendor.OFVendorData;

/**
 * Class that represents the vendor data in the queue modify request
 *
 * @author Andrew Ferguson (adf@cs.brown.edu)
 */
public class OFQueueModifyVendorData extends OFQueueVendorData {

    protected static Instantiable<OFVendorData> instantiable =
            new Instantiable<OFVendorData>() {
                public OFVendorData instantiate() {
                    return new OFQueueModifyVendorData();
                }
            };

    /**
     * @return a subclass of Instantiable<OFVendorData> that instantiates
     *         an instance of OFQueueModifyVendorData.
     */
    public static Instantiable<OFVendorData> getInstantiable() {
        return instantiable;
    }

    /**
     * The data type value for a queue modify request
     */
    public static final int OFP_EXT_QUEUE_MODIFY = 0;

    public OFQueueModifyVendorData() {
        super(OFP_EXT_QUEUE_MODIFY);
    }
}
