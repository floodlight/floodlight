/**
*    Copyright 2011, Big Switch Networks, Inc. 
*    Originally created by David Erickson & Rob Sherwood, Stanford University
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

package org.openflow.vendor.nicira;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Class that represents the vendor data in the role request
 * extension implemented by Open vSwitch to support high availability.
 * 
 * @author Rob Vaterlaus (rob.vaterlaus@bigswitch.com)
 */
public class OFRoleVendorData extends OFNiciraVendorData {
    
    /**
     * Role value indicating that the controller is in the OTHER role.
     */
    public static final int NX_ROLE_OTHER = 0;
    
    /**
     * Role value indicating that the controller is in the MASTER role.
     */
    public static final int NX_ROLE_MASTER = 1;
    
    /**
     * Role value indicating that the controller is in the SLAVE role.
     */
    public static final int NX_ROLE_SLAVE = 2;

    protected int role;

    /** 
     * Construct an uninitialized OFRoleVendorData
     */
    public OFRoleVendorData() {
        super();
    }
    
    /**
     * Construct an OFRoleVendorData with the specified data type
     * (i.e. either request or reply) and an unspecified role.
     * @param dataType
     */
    public OFRoleVendorData(int dataType) {
        super(dataType);
    }
    
    /**
     * Construct an OFRoleVendorData with the specified data type
     * (i.e. either request or reply) and role (i.e. one of of
     * master, slave, or other).
     * @param dataType either role request or role reply data type
     */
    public OFRoleVendorData(int dataType, int role) {
        super(dataType);
        this.role = role;
    }
    /**
     * @return the role value of the role vendor data
     */
    public int getRole() {
        return role;
    }
    
    /**
     * @param role the role value of the role vendor data
     */
    public void setRole(int role) {
        this.role = role;
    }

    /**
     * @return the total length of the role vendor data
     */
    @Override
    public int getLength() {
        return super.getLength() + 4;
    }
    
    /**
     * Read the role vendor data from the ChannelBuffer
     * @param data the channel buffer from which we're deserializing
     * @param length the length to the end of the enclosing message
     */
    public void readFrom(ChannelBuffer data, int length) {
        super.readFrom(data, length);
        role = data.readInt();
    }

    /**
     * Write the role vendor data to the ChannelBuffer
     * @param data the channel buffer to which we're serializing
     */
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeInt(role);
    }
}
