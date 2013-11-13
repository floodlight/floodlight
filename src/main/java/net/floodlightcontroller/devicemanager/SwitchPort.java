/**
*    Copyright 2012 Big Switch Networks, Inc.
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

package net.floodlightcontroller.devicemanager;

import org.openflow.util.HexString;

import net.floodlightcontroller.core.web.serializers.DPIDSerializer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * A simple switch DPID/port pair
 * This class is immutable
 * @author readams
 *
 */
public class SwitchPort {
    @JsonSerialize(using=ToStringSerializer.class)
    public enum ErrorStatus {
        DUPLICATE_DEVICE("duplicate-device");

        private String value;
        ErrorStatus(String v) {
            value = v;
        }

        @Override
        public String toString() {
            return value;
        }

        public static ErrorStatus fromString(String str) {
            for (ErrorStatus m : ErrorStatus.values()) {
                if (m.value.equals(str)) {
                    return m;
                }
            }
            return null;
        }
    }

    private final long switchDPID;
    private final int port;
    private final ErrorStatus errorStatus;

    /**
     * Simple constructor
     * @param switchDPID the dpid
     * @param port the port
     * @param errorStatus any error status for the switch port
     */
    public SwitchPort(long switchDPID, int port, ErrorStatus errorStatus) {
        super();
        this.switchDPID = switchDPID;
        this.port = port;
        this.errorStatus = errorStatus;
    }

    /**
     * Simple constructor
     * @param switchDPID the dpid
     * @param port the port
     */
    public SwitchPort(long switchDPID, int port) {
        super();
        this.switchDPID = switchDPID;
        this.port = port;
        this.errorStatus = null;
    }

    // ***************
    // Getters/Setters
    // ***************

    @JsonSerialize(using=DPIDSerializer.class)
    public long getSwitchDPID() {
        return switchDPID;
    }

    public int getPort() {
        return port;
    }

    public ErrorStatus getErrorStatus() {
        return errorStatus;
    }

    // ******
    // Object
    // ******

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                        + ((errorStatus == null)
                                ? 0
                                : errorStatus.hashCode());
        result = prime * result + port;
        result = prime * result + (int) (switchDPID ^ (switchDPID >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        SwitchPort other = (SwitchPort) obj;
        if (errorStatus != other.errorStatus) return false;
        if (port != other.port) return false;
        if (switchDPID != other.switchDPID) return false;
        return true;
    }

    @Override
    public String toString() {
        return "SwitchPort [switchDPID=" + HexString.toHexString(switchDPID) +
               ", port=" + port + ", errorStatus=" + errorStatus + "]";
    }

}