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
import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.openflow.protocol.factory.MessageParseException;
import org.openflow.protocol.factory.OFMessageFactory;
import org.openflow.protocol.factory.OFMessageFactoryAware;
import org.openflow.util.U16;

/**
 * Represents an ofp_error_msg
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 * @author Rob Sherwood (rob.sherwood@stanford.edu)
 */
public class OFError extends OFMessage implements OFMessageFactoryAware {
    public static int MINIMUM_LENGTH = 12;

    public enum OFErrorType {
        // OFPET_VENDOR_ERROR is an extension that was added in Open vSwitch and isn't
        // in the OF 1.0 spec, but it was easier to add it here instead of adding
        // generic support for extensible vendor-defined error messages.
        // It uses the random value 0xb0c2 to avoid conflicts with other possible new
        // error types. Support for vendor-defined extended errors has been standardized
        // in the OF 1.2 spec, so this workaround is only needed for 1.0.
        OFPET_HELLO_FAILED, OFPET_BAD_REQUEST, OFPET_BAD_ACTION, OFPET_FLOW_MOD_FAILED, OFPET_PORT_MOD_FAILED, OFPET_QUEUE_OP_FAILED, OFPET_VENDOR_ERROR((short)0xb0c2);

        protected short value;

        private OFErrorType() {
            this.value = (short) this.ordinal();
        }

        private OFErrorType(short value) {
            this.value = value;
        }

        public short getValue() {
            return value;
        }
    }

    public enum OFHelloFailedCode {
        OFPHFC_INCOMPATIBLE, OFPHFC_EPERM
    }

    public enum OFBadRequestCode {
        OFPBRC_BAD_VERSION, OFPBRC_BAD_TYPE, OFPBRC_BAD_STAT, OFPBRC_BAD_VENDOR, OFPBRC_BAD_SUBTYPE, OFPBRC_EPERM, OFPBRC_BAD_LEN, OFPBRC_BUFFER_EMPTY, OFPBRC_BUFFER_UNKNOWN
    }

    public enum OFBadActionCode {
        OFPBAC_BAD_TYPE, OFPBAC_BAD_LEN, OFPBAC_BAD_VENDOR, OFPBAC_BAD_VENDOR_TYPE, OFPBAC_BAD_OUT_PORT, OFPBAC_BAD_ARGUMENT, OFPBAC_EPERM, OFPBAC_TOO_MANY, OFPBAC_BAD_QUEUE
    }

    public enum OFFlowModFailedCode {
        OFPFMFC_ALL_TABLES_FULL, OFPFMFC_OVERLAP, OFPFMFC_EPERM, OFPFMFC_BAD_EMERG_TIMEOUT, OFPFMFC_BAD_COMMAND, OFPFMFC_UNSUPPORTED
    }

    public enum OFPortModFailedCode {
        OFPPMFC_BAD_PORT, OFPPMFC_BAD_HW_ADDR
    }

    public enum OFQueueOpFailedCode {
        OFPQOFC_BAD_PORT, OFPQOFC_BAD_QUEUE, OFPQOFC_EPERM
    }

    protected short errorType;
    protected short errorCode;
    protected int vendor;
    protected int vendorErrorType;
    protected short vendorErrorCode;
    protected OFMessageFactory factory;
    protected byte[] error;
    protected boolean errorIsAscii;

    public OFError() {
        super();
        this.type = OFType.ERROR;
        this.length = U16.t(MINIMUM_LENGTH);
    }

    /** convenience constructor */
    public OFError(OFErrorType errorType) {
        this();
        setErrorType(errorType);
    }

    /**
     * @return the errorType
     */
    public short getErrorType() {
        return errorType;
    }

    /**
     * @param errorType
     *            the errorType to set
     */
    public void setErrorType(short errorType) {
        this.errorType = errorType;
    }

    public void setErrorType(OFErrorType type) {
        this.errorType = type.getValue();
    }

    /**
     * @return true if the error is an extended vendor error
     */
    public boolean isVendorError() {
        return errorType == OFErrorType.OFPET_VENDOR_ERROR.getValue();
    }

    /**
     * @return the errorCode
     */
    public short getErrorCode() {
        return errorCode;
    }

    /**
     * @param errorCode
     *            the errorCode to set
     */
    public void setErrorCode(OFHelloFailedCode code) {
        this.errorCode = (short) code.ordinal();
    }

    public void setErrorCode(short errorCode) {
        this.errorCode = errorCode;
    }

    public void setErrorCode(OFBadRequestCode code) {
        this.errorCode = (short) code.ordinal();
    }

    public void setErrorCode(OFBadActionCode code) {
        this.errorCode = (short) code.ordinal();
    }

    public void setErrorCode(OFFlowModFailedCode code) {
        this.errorCode = (short) code.ordinal();
    }

    public void setErrorCode(OFPortModFailedCode code) {
        this.errorCode = (short) code.ordinal();
    }

    public void setErrorCode(OFQueueOpFailedCode code) {
        this.errorCode = (short) code.ordinal();
    }

    public int getVendorErrorType() {
        return vendorErrorType;
    }

    public void setVendorErrorType(int vendorErrorType) {
        this.vendorErrorType = vendorErrorType;
    }

    public short getVendorErrorCode() {
        return vendorErrorCode;
    }

    public void setVendorErrorCode(short vendorErrorCode) {
        this.vendorErrorCode = vendorErrorCode;
    }

    public OFMessage getOffendingMsg() throws MessageParseException {
        // should only have one message embedded; if more than one, just
        // grab first
        if (this.error == null)
            return null;
        ChannelBuffer errorMsg = ChannelBuffers.wrappedBuffer(this.error);
        if (factory == null)
            throw new RuntimeException("MessageFactory not set");

        List<OFMessage> msglist = this.factory.parseMessage(errorMsg);
        if (msglist == null)
                return null;
        return msglist.get(0);
    }

    /**
     * Write this offending message into the payload of the Error message
     *
     * @param offendingMsg
     */

    public void setOffendingMsg(OFMessage offendingMsg) {
        if (offendingMsg == null) {
            super.setLengthU(MINIMUM_LENGTH);
        } else {
            this.error = new byte[offendingMsg.getLengthU()];
            ChannelBuffer data = ChannelBuffers.wrappedBuffer(this.error);
            data.writerIndex(0);
            offendingMsg.writeTo(data);
            super.setLengthU(MINIMUM_LENGTH + offendingMsg.getLengthU());
        }
    }

    public OFMessageFactory getFactory() {
        return factory;
    }

    @Override
    public void setMessageFactory(OFMessageFactory factory) {
        this.factory = factory;
    }

    /**
     * @return the error
     */
    public byte[] getError() {
        return error;
    }

    /**
     * @param error
     *            the error to set
     */
    public void setError(byte[] error) {
        this.error = error;
    }

    /**
     * @return the errorIsAscii
     */
    public boolean isErrorIsAscii() {
        return errorIsAscii;
    }

    /**
     * @param errorIsAscii
     *            the errorIsAscii to set
     */
    public void setErrorIsAscii(boolean errorIsAscii) {
        this.errorIsAscii = errorIsAscii;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);
        this.errorType = data.readShort();
        this.errorCode = data.readShort();
        int dataLength = this.getLengthU() - MINIMUM_LENGTH;
        if (dataLength > 0) {
            this.error = new byte[dataLength];
            data.readBytes(this.error);
            if (this.errorType == OFErrorType.OFPET_HELLO_FAILED.getValue())
                this.errorIsAscii = true;
        }
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeShort(errorType);
        data.writeShort(errorCode);
        if (error != null)
            data.writeBytes(error);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Arrays.hashCode(error);
        result = prime * result + errorCode;
        result = prime * result + (errorIsAscii ? 1231 : 1237);
        result = prime * result + errorType;
        return result;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        OFError other = (OFError) obj;
        if (!Arrays.equals(error, other.error))
            return false;
        if (errorCode != other.errorCode)
            return false;
        if (errorIsAscii != other.errorIsAscii)
            return false;
        if (errorType != other.errorType)
            return false;
        return true;
    }

}
