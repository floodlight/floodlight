/**
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
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 * The class representing LLDP Organizationally Specific TLV.
 *
 * @author Sho Shimizu (sho.shimizu@gmail.com)
 */
public class LLDPOrganizationalTLV extends LLDPTLV {
    public static final int OUI_LENGTH = 3;
    public static final int SUBTYPE_LENGTH = 1;
    public static final byte ORGANIZATIONAL_TLV_TYPE = 127;
    public static final int MAX_INFOSTRING_LENGTH = 507;

    protected byte[] oui;
    protected byte subType;
    private byte[] infoString;

    public LLDPOrganizationalTLV() {
        type = ORGANIZATIONAL_TLV_TYPE;
    }

    /**
     * Set the value of OUI.
     * @param oui The value of OUI to be set.
     * @return This LLDP Organizationally Specific TLV.
     */
    public LLDPOrganizationalTLV setOUI(byte[] oui) {
        if (oui.length != OUI_LENGTH) {
            throw new IllegalArgumentException("The length of OUI must be " + OUI_LENGTH +
                ", but it is " + oui.length);
        }
        this.oui = Arrays.copyOf(oui, oui.length);
        return this;
    }

    /**
     * Returns the value of the OUI.
     * @return The value of the OUI .
     */
    public byte[] getOUI() {
        return Arrays.copyOf(oui, oui.length);
    }

    /**
     * Set the value of sub type.
     * @param subType The value of sub type to be set.
     * @return This LLDP Organizationally Specific TLV.
     */
    public LLDPOrganizationalTLV setSubType(byte subType) {
        this.subType = subType;
        return this;
    }

    /**
     * Returns the value of the sub type.
     * @return The value of the sub type.
     */
    public byte getSubType() {
        return subType;
    }

    /**
     * Set the value of information string.
     * @param infoString the byte array of the value of information string.
     * @return This LLDP Organizationally Specific TLV.
     */
    public LLDPOrganizationalTLV setInfoString(byte[] infoString) {
        if (infoString.length > MAX_INFOSTRING_LENGTH) {
            throw new IllegalArgumentException("The length of infoString cannot exceed " + MAX_INFOSTRING_LENGTH);
        }
        this.infoString = Arrays.copyOf(infoString, infoString.length);
        return this;
    }

    /**
     * Set the value of information string.
     * The String value is automatically converted into byte array with UTF-8 encoding.
     * @param infoString the String value of information string.
     * @return This LLDP Organizationally Specific TLV.
     */
    public LLDPOrganizationalTLV setInfoString(String infoString) {
        byte[] infoStringBytes = infoString.getBytes(Charset.forName("UTF-8"));
        return setInfoString(infoStringBytes);
    }

    /**
     * Returns the value of information string.
     * @return the value of information string.
     */
    public byte[] getInfoString() {
        return Arrays.copyOf(infoString, infoString.length);
    }

    @Override
    public byte[] serialize() {
        int valueLength = OUI_LENGTH + SUBTYPE_LENGTH + infoString.length;
        value = new byte[valueLength];
        ByteBuffer bb = ByteBuffer.wrap(value);
        bb.put(oui);
        bb.put(subType);
        bb.put(infoString);
        return super.serialize();
    }

    @Override
    public LLDPTLV deserialize(ByteBuffer bb) {
        super.deserialize(bb);
        ByteBuffer optionalField = ByteBuffer.wrap(value);

        byte[] oui = new byte[OUI_LENGTH];
        optionalField.get(oui);
        setOUI(oui);

        setSubType(optionalField.get());

        byte[] infoString = new byte[getLength() - OUI_LENGTH - SUBTYPE_LENGTH];
        optionalField.get(infoString);
        setInfoString(infoString);
        return this;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + Arrays.hashCode(infoString);
        result = prime * result + Arrays.hashCode(oui);
        result = prime * result + subType;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        LLDPOrganizationalTLV other = (LLDPOrganizationalTLV) obj;
        if (!Arrays.equals(infoString, other.infoString)) return false;
        if (!Arrays.equals(oui, other.oui)) return false;
        if (subType != other.subType) return false;
        return true;
    }
}
