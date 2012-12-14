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

import org.openflow.protocol.vendor.OFBasicVendorDataType;
import org.openflow.protocol.vendor.OFBasicVendorId;
import org.openflow.protocol.vendor.OFVendorId;

public class OFOpenFlowVendorExtensions {
    private static boolean initialized = false;

    public static synchronized void initialize() {
        if (initialized)
            return;

        // Configure openflowj to be able to parse the OpenFlow extensions.
        OFBasicVendorId openflowVendorId =
                new OFBasicVendorId(OFOpenFlowVendorData.OF_VENDOR_ID, 4);
        OFVendorId.registerVendorId(openflowVendorId);

        OFBasicVendorDataType queueModifyVendorData =
                new OFBasicVendorDataType(OFQueueModifyVendorData.OFP_EXT_QUEUE_MODIFY,
                        OFQueueModifyVendorData.getInstantiable());
        openflowVendorId.registerVendorDataType(queueModifyVendorData);

        OFBasicVendorDataType queueDeleteVendorData =
                new OFBasicVendorDataType(OFQueueDeleteVendorData.OFP_EXT_QUEUE_DELETE,
                        OFQueueModifyVendorData.getInstantiable());
        openflowVendorId.registerVendorDataType(queueDeleteVendorData);

        initialized = true;
    }
}
