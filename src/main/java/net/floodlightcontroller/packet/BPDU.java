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

package net.floodlightcontroller.packet;

import java.nio.ByteBuffer;

/**
 * This class is a Rapid Spanning Tree Protocol
 * Bridge Protocol Data Unit
 * @author alexreimers
 */
public class BPDU extends BasePacket {
    public enum BPDUType {
        CONFIG,
        TOPOLOGY_CHANGE;
    }
    
    private final long destMac = 0x0180c2000000L; // 01-80-c2-00-00-00
    
    // TODO - check this for RSTP
    private LLC llcHeader;
    private short protocolId = 0;
    private byte version = 0;
    private byte type;
    private byte flags;
    private byte[] rootBridgeId;
    private int rootPathCost;
    private byte[] senderBridgeId; // switch cluster MAC
    private short portId; // port it was transmitted from
    private short messageAge; // 256ths of a second
    private short maxAge; // 256ths of a second
    private short helloTime; // 256ths of a second
    private short forwardDelay; // 256ths of a second
    
    public BPDU(BPDUType type) {
        rootBridgeId = new byte[8];
        senderBridgeId = new byte[8];
        
        llcHeader = new LLC();
        llcHeader.setDsap((byte) 0x42);
        llcHeader.setSsap((byte) 0x42);
        llcHeader.setCtrl((byte) 0x03);
        
        switch(type) {
            case CONFIG:
                this.type = 0x0;
                break;
            case TOPOLOGY_CHANGE:
                this.type = (byte) 0x80; // 1000 0000
                break;
            default:
                this.type = 0;
                break;
        }
    }
    
    @Override
    public byte[] serialize() {
        byte[] data;
        // TODO check these
        if (type == 0x0) { 
            // config
            data = new byte[38];
        } else {
            // topology change
            data = new byte[7]; // LLC + TC notification
        }
        
        ByteBuffer bb = ByteBuffer.wrap(data);
        // Serialize the LLC header
        byte[] llc = llcHeader.serialize();
        bb.put(llc, 0, llc.length);
        bb.putShort(protocolId);
        bb.put(version);
        bb.put(type);
        
        if (type == 0x0) {
            bb.put(flags);
            bb.put(rootBridgeId, 0, rootBridgeId.length);
            bb.putInt(rootPathCost);
            bb.put(senderBridgeId, 0, senderBridgeId.length);
            bb.putShort(portId);
            bb.putShort(messageAge);
            bb.putShort(maxAge);
            bb.putShort(helloTime);
            bb.putShort(forwardDelay);
        }
        
        return data;
    }

    @Override
    public IPacket deserialize(byte[] data, int offset, int length) {
        ByteBuffer bb = ByteBuffer.wrap(data, offset, length);
        
        // LLC header
        llcHeader.deserialize(data, offset, 3);
        
        this.protocolId = bb.getShort();
        this.version = bb.get();
        this.type = bb.get();
        
        // These fields only exist if it's a configuration BPDU
        if (this.type == 0x0) {
            this.flags = bb.get();
            bb.get(rootBridgeId, 0, 6);
            this.rootPathCost = bb.getInt();
            bb.get(this.senderBridgeId, 0, 6);
            this.portId = bb.getShort();
            this.messageAge = bb.getShort();
            this.maxAge = bb.getShort();
            this.helloTime = bb.getShort();
            this.forwardDelay = bb.getShort();
        }
        // TODO should we set other fields to 0?
        
        return this;
    }

    public long getDestMac() {
        return destMac;
    }
}
