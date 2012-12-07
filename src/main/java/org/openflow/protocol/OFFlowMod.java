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

import java.util.LinkedList;
import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.factory.OFActionFactory;
import org.openflow.protocol.factory.OFActionFactoryAware;
import org.openflow.util.U16;

/**
 * Represents an ofp_flow_mod message
 * @author David Erickson (daviderickson@cs.stanford.edu)
 *
 */
public class OFFlowMod extends OFMessage implements OFActionFactoryAware, Cloneable {
    public static int MINIMUM_LENGTH = 72;

    public static final short OFPFC_ADD = 0;                /* New flow. */
    public static final short OFPFC_MODIFY = 1;             /* Modify all matching flows. */
    public static final short OFPFC_MODIFY_STRICT = 2;      /* Modify entry strictly matching wildcards */
    public static final short OFPFC_DELETE=3;               /* Delete all matching flows. */
    public static final short OFPFC_DELETE_STRICT =4;       /* Strictly match wildcards and priority. */

    // Open Flow Flow Mod Flags. Use "or" operation to set multiple flags
    public static final short OFPFF_SEND_FLOW_REM = 0x1; // 1 << 0
    public static final short OFPFF_CHECK_OVERLAP = 0x2; // 1 << 1
    public static final short OFPFF_EMERG         = 0x4; // 1 << 2

    protected OFActionFactory actionFactory;
    protected OFMatch match;
    protected long cookie;
    protected short command;
    protected short idleTimeout;
    protected short hardTimeout;
    protected short priority;
    protected int bufferId;
    protected short outPort;
    protected short flags;
    protected List<OFAction> actions;

    public OFFlowMod() {
        super();
        this.outPort = OFPort.OFPP_NONE.getValue();
        this.type = OFType.FLOW_MOD;
        this.length = U16.t(MINIMUM_LENGTH);
    }

    /**
     * Get buffer_id
     * @return
     */
    public int getBufferId() {
        return this.bufferId;
    }

    /**
     * Set buffer_id
     * @param bufferId
     */
    public OFFlowMod setBufferId(int bufferId) {
        this.bufferId = bufferId;
        return this;
    }

    /**
     * Get cookie
     * @return
     */
    public long getCookie() {
        return this.cookie;
    }

    /**
     * Set cookie
     * @param cookie
     */
    public OFFlowMod setCookie(long cookie) {
        this.cookie = cookie;
        return this;
    }

    /**
     * Get command
     * @return
     */
    public short getCommand() {
        return this.command;
    }

    /**
     * Set command
     * @param command
     */
    public OFFlowMod setCommand(short command) {
        this.command = command;
        return this;
    }

    /**
     * Get flags
     * @return
     */
    public short getFlags() {
        return this.flags;
    }

    /**
     * Set flags
     * @param flags
     */
    public OFFlowMod setFlags(short flags) {
        this.flags = flags;
        return this;
    }

    /**
     * Get hard_timeout
     * @return
     */
    public short getHardTimeout() {
        return this.hardTimeout;
    }

    /**
     * Set hard_timeout
     * @param hardTimeout
     */
    public OFFlowMod setHardTimeout(short hardTimeout) {
        this.hardTimeout = hardTimeout;
        return this;
    }

    /**
     * Get idle_timeout
     * @return
     */
    public short getIdleTimeout() {
        return this.idleTimeout;
    }

    /**
     * Set idle_timeout
     * @param idleTimeout
     */
    public OFFlowMod setIdleTimeout(short idleTimeout) {
        this.idleTimeout = idleTimeout;
        return this;
    }

    /**
     * Gets a copy of the OFMatch object for this FlowMod, changes to this
     * object do not modify the FlowMod
     * @return
     */
    public OFMatch getMatch() {
        return this.match;
    }

    /**
     * Set match
     * @param match
     */
    public OFFlowMod setMatch(OFMatch match) {
        this.match = match;
        return this;
    }

    /**
     * Get out_port
     * @return
     */
    public short getOutPort() {
        return this.outPort;
    }

    /**
     * Set out_port
     * @param outPort
     */
    public OFFlowMod setOutPort(short outPort) {
        this.outPort = outPort;
        return this;
    }

    /**
     * Set out_port
     * @param port
     */
    public OFFlowMod setOutPort(OFPort port) {
        this.outPort = port.getValue();
        return this;
    }

    /**
     * Get priority
     * @return
     */
    public short getPriority() {
        return this.priority;
    }

    /**
     * Set priority
     * @param priority
     */
    public OFFlowMod setPriority(short priority) {
        this.priority = priority;
        return this;
    }

    /**
     * Returns read-only copies of the actions contained in this Flow Mod
     * @return a list of ordered OFAction objects
     */
    public List<OFAction> getActions() {
        return this.actions;
    }

    /**
     * Sets the list of actions this Flow Mod contains
     * @param actions a list of ordered OFAction objects
     */
    public OFFlowMod setActions(List<OFAction> actions) {
        this.actions = actions;
        return this;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);
        if (this.match == null)
            this.match = new OFMatch();
        this.match.readFrom(data);
        this.cookie = data.readLong();
        this.command = data.readShort();
        this.idleTimeout = data.readShort();
        this.hardTimeout = data.readShort();
        this.priority = data.readShort();
        this.bufferId = data.readInt();
        this.outPort = data.readShort();
        this.flags = data.readShort();
        if (this.actionFactory == null)
            throw new RuntimeException("OFActionFactory not set");
        this.actions = this.actionFactory.parseActions(data, getLengthU() -
                MINIMUM_LENGTH);
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        this.match.writeTo(data);
        data.writeLong(cookie);
        data.writeShort(command);
        data.writeShort(idleTimeout);
        data.writeShort(hardTimeout);
        data.writeShort(priority);
        data.writeInt(bufferId);
        data.writeShort(outPort);
        data.writeShort(flags);
        if (actions != null) {
            for (OFAction action : actions) {
                action.writeTo(data);
            }
        }
    }

    @Override
    public void setActionFactory(OFActionFactory actionFactory) {
        this.actionFactory = actionFactory;
    }

    @Override
    public int hashCode() {
        final int prime = 227;
        int result = super.hashCode();
        result = prime * result + ((actions == null) ? 0 : actions.hashCode());
        result = prime * result + bufferId;
        result = prime * result + command;
        result = prime * result + (int) (cookie ^ (cookie >>> 32));
        result = prime * result + flags;
        result = prime * result + hardTimeout;
        result = prime * result + idleTimeout;
        result = prime * result + ((match == null) ? 0 : match.hashCode());
        result = prime * result + outPort;
        result = prime * result + priority;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (!(obj instanceof OFFlowMod)) {
            return false;
        }
        OFFlowMod other = (OFFlowMod) obj;
        if (actions == null) {
            if (other.actions != null) {
                return false;
            }
        } else if (!actions.equals(other.actions)) {
            return false;
        }
        if (bufferId != other.bufferId) {
            return false;
        }
        if (command != other.command) {
            return false;
        }
        if (cookie != other.cookie) {
            return false;
        }
        if (flags != other.flags) {
            return false;
        }
        if (hardTimeout != other.hardTimeout) {
            return false;
        }
        if (idleTimeout != other.idleTimeout) {
            return false;
        }
        if (match == null) {
            if (other.match != null) {
                return false;
            }
        } else if (!match.equals(other.match)) {
            return false;
        }
        if (outPort != other.outPort) {
            return false;
        }
        if (priority != other.priority) {
            return false;
        }
        return true;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#clone()
     */
    @Override
    public OFFlowMod clone() throws CloneNotSupportedException {
        OFMatch neoMatch = match.clone();
        OFFlowMod flowMod= (OFFlowMod) super.clone();
        flowMod.setMatch(neoMatch);
        List<OFAction> neoActions = new LinkedList<OFAction>();
        for(OFAction action: this.actions)
            neoActions.add((OFAction) action.clone());
        flowMod.setActions(neoActions);
        return flowMod;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "OFFlowMod [actionFactory=" + actionFactory + ", actions="
                + actions + ", bufferId=" + bufferId + ", command=" + command
                + ", cookie=" + cookie + ", flags=" + flags + ", hardTimeout="
                + hardTimeout + ", idleTimeout=" + idleTimeout + ", match="
                + match + ", outPort=" + outPort + ", priority=" + priority
                + ", length=" + length + ", type=" + type + ", version="
                + version + ", xid=" + xid + "]";
    }
}
