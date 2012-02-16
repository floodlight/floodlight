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

import java.util.Arrays;

import junit.framework.TestCase;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.vendor.OFByteArrayVendorData;
import org.openflow.protocol.vendor.OFBasicVendorDataType;
import org.openflow.protocol.vendor.OFBasicVendorId;
import org.openflow.protocol.vendor.OFVendorData;
import org.openflow.protocol.vendor.OFVendorId;
import org.openflow.util.OFTestCase;

public class OFVendorTest extends OFTestCase {

    public static int ACME_VENDOR_ID = 0x00112233;
    
    static class AcmeVendorData implements OFVendorData {
        protected int dataType;
        
        public int getLength() {
            return 4;
        }
        
        public void readFrom(ChannelBuffer data, int length) {
            dataType = data.readInt();
        }
        
        public void writeTo(ChannelBuffer data) {
            data.writeInt(dataType);
        }
    }
    
    static class AcmeVendorData1 extends AcmeVendorData {
        public short flags;
        public short value;
        
        public static int DATA_TYPE = 1;
        
        public AcmeVendorData1() {
        }
        
        public AcmeVendorData1(short flags, short value) {
            this.dataType = DATA_TYPE;
            this.flags = flags;
            this.value = value;
        }
        
        public short getFlags() {
            return flags;
        }
        
        public short getValue() {
            return value;
        }
        
        public int getLength() {
            return 8;
        }
        
        public void readFrom(ChannelBuffer data, int length) {
            super.readFrom(data, length);
            flags = data.readShort();
            value = data.readShort();

        }
        public void writeTo(ChannelBuffer data) {
            super.writeTo(data);
            data.writeShort(flags);
            data.writeShort(value);
        }
        
        public static Instantiable<OFVendorData> getInstantiable() {
            return new Instantiable<OFVendorData>() {
                public OFVendorData instantiate() {
                    return new AcmeVendorData1();
                }
            };
        }
    }
    
    static class AcmeVendorData2 extends AcmeVendorData {
        public int type;
        public int subtype;

        public static int DATA_TYPE = 2;

        public AcmeVendorData2() {
        }
        
        public AcmeVendorData2(int type, int subtype) {
            this.dataType = DATA_TYPE;
            this.type = type;
            this.subtype = subtype;
        }
        
        public int getType() {
            return type;
        }
        
        public int getSubtype() {
            return subtype;
        }
        
        public int getLength() {
            return 12;
        }
        
        public void readFrom(ChannelBuffer data, int length) {
            super.readFrom(data, length);
            type = data.readShort();
            subtype = data.readShort();

        }
        public void writeTo(ChannelBuffer data) {
            super.writeTo(data);
            data.writeShort(type);
            data.writeShort(subtype);
        }
        
        public static Instantiable<OFVendorData> getInstantiable() {
            return new Instantiable<OFVendorData>() {
                public OFVendorData instantiate() {
                    return new AcmeVendorData2();
                }
            };
        }
    }
    
    {
        OFBasicVendorId acmeVendorId = new OFBasicVendorId(ACME_VENDOR_ID, 4);
        OFVendorId.registerVendorId(acmeVendorId);
        OFBasicVendorDataType acmeVendorData1 = new OFBasicVendorDataType(
            AcmeVendorData1.DATA_TYPE, AcmeVendorData1.getInstantiable());
        acmeVendorId.registerVendorDataType(acmeVendorData1);
        OFBasicVendorDataType acmeVendorData2 = new OFBasicVendorDataType(
            AcmeVendorData2.DATA_TYPE, AcmeVendorData2.getInstantiable());
        acmeVendorId.registerVendorDataType(acmeVendorData2);
    }
    
    private OFVendor makeVendorMessage(int vendor) {
        OFVendor msg = (OFVendor) messageFactory.getMessage(OFType.VENDOR);
        msg.setVendorDataFactory(new BasicFactory());
        msg.setVendor(vendor);
        return msg;
    }
    
    public void testWriteRead() throws Exception {
        OFVendor msg = makeVendorMessage(1);
        ChannelBuffer bb = ChannelBuffers.dynamicBuffer();
        bb.clear();
        msg.writeTo(bb);
        msg.readFrom(bb);
        TestCase.assertEquals(1, msg.getVendor());
    }
    
    public void testVendorData() throws Exception {
        OFVendor msg = makeVendorMessage(ACME_VENDOR_ID);
        OFVendorData vendorData = new AcmeVendorData1((short)11, (short)22);
        msg.setVendorData(vendorData);
        msg.setLengthU(OFVendor.MINIMUM_LENGTH + vendorData.getLength());
        ChannelBuffer bb = ChannelBuffers.dynamicBuffer();
        bb.clear();
        msg.writeTo(bb);
        msg.readFrom(bb);
        assertEquals(ACME_VENDOR_ID, msg.getVendor());
        AcmeVendorData1 vendorData1 = (AcmeVendorData1) msg.getVendorData();
        assertEquals(11, vendorData1.getFlags());
        assertEquals(22, vendorData1.getValue());
        
        vendorData = new AcmeVendorData2(33, 44);
        msg.setVendorData(vendorData);
        msg.setLengthU(OFVendor.MINIMUM_LENGTH + vendorData.getLength());
        bb.clear();
        msg.writeTo(bb);
        msg.readFrom(bb);
        assertEquals(ACME_VENDOR_ID, msg.getVendor());
        AcmeVendorData2 vendorData2 = (AcmeVendorData2) msg.getVendorData();
        assertEquals(33, vendorData2.getType());
        assertEquals(44, vendorData2.getSubtype());
        
        final int DUMMY_VENDOR_ID = 55;
        msg.setVendor(DUMMY_VENDOR_ID);
        byte[] genericVendorDataBytes = new byte[] {0x55, 0x66};
        vendorData = new OFByteArrayVendorData(genericVendorDataBytes);
        msg.setVendorData(vendorData);
        msg.setLengthU(OFVendor.MINIMUM_LENGTH + vendorData.getLength());
        bb.clear();
        msg.writeTo(bb);
        msg.readFrom(bb);
        assertEquals(DUMMY_VENDOR_ID, msg.getVendor());
        OFByteArrayVendorData genericVendorData = (OFByteArrayVendorData) msg.getVendorData();
        assertTrue(Arrays.equals(genericVendorDataBytes, genericVendorData.getBytes()));
    }
}
