package com.bigswitch.floodlight.vendor;

import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.Instantiable;
import org.openflow.protocol.vendor.OFVendorData;

public class OFInterfaceIPReplyVendorData extends OFBigSwitchVendorData {
    
    protected List<OFInterfaceVendorData> interfaces;
    protected int length;
    
    protected static Instantiable<OFVendorData> instantiable =
            new Instantiable<OFVendorData>() {
                public OFVendorData instantiate() {
                    return new OFInterfaceIPReplyVendorData();
                }
    };
    
    /**
     * @return a subclass of Instantiable<OFVendorData> that instantiates
     *         an instance of OFInterfaceIPReplyVendorData.
     */
    public static Instantiable<OFVendorData> getInstantiable() {
        return instantiable;
    }

    /**
     * Opcode/dataType to reply with IP addresses of all interfaces
     */
    public static final int BSN_GET_INTERFACE_IP_REPLY = 10;

    /**
     * Construct an interface IP reply vendor data 
     */
    public OFInterfaceIPReplyVendorData() {
        super(BSN_GET_INTERFACE_IP_REPLY);   
    }
    
    /**
     * @return the total length of the vendor-data part of the interface IP reply 
     * message. The OF header (8B) and vendor (4B) are taken care of by the
     * OFVendor class MINIMUM_LENGTH. This method returns the length of the 
     * vendor-extension-subtype (4B) + the length of the interfaces
     */
    @Override
    public int getLength() {
        return length;
    }
    
    /**
     * Set the length of this message
     *
     * @param length
     */
    public void setLength(int length) {
        this.length = length;
        
    }
    
    /**
     * @return the interfaces
     */
    public List<OFInterfaceVendorData> getInterfaces() {
        return interfaces;
    }
    
    /**
     * @param intfs  the ones to set
     */
    public void setInterfaces(List<OFInterfaceVendorData> intfs) {
        this.interfaces = intfs;
        if (intfs == null) {
            this.setLength(super.getLength());
        } else {
            this.setLength(super.getLength() + intfs.size()
                    * OFInterfaceVendorData.MINIMUM_LENGTH);
        }
    }
    
    /**
     * Read from the ChannelBuffer
     * @param data the channel buffer from which we're deserializing
     * @param length the length to the end of the enclosing message
     */
    @Override
    public void readFrom(ChannelBuffer data, int length) {
        //datatype read by super class
        super.readFrom(data, length);
        
        if (this.interfaces == null) {
            this.interfaces = new ArrayList<OFInterfaceVendorData>();
        } else {
            this.interfaces.clear();
        }
        int intfCount = (length - 4)
                / OFInterfaceVendorData.MINIMUM_LENGTH;
        
        OFInterfaceVendorData intf;
        for (int i = 0; i < intfCount; ++i) {
            intf = new OFInterfaceVendorData();
            intf.readFrom(data);
            this.interfaces.add(intf);
        }
    }

    /**
     * Write to the ChannelBuffer
     * @param data the channel buffer to which we're serializing
     */
    @Override
    public void writeTo(ChannelBuffer data) {
        // datatype written by super class
        super.writeTo(data);
        if (this.interfaces != null) {
            for (OFInterfaceVendorData intf : this.interfaces) {
                intf.writeTo(data);
            }
        }
    }
    
}
