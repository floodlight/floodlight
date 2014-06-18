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

import java.lang.reflect.Constructor;

/**
 * List of OpenFlow types and mappings to wire protocol value and derived
 * classes
 *
 * @author Rob Sherwood (rob.sherwood@stanford.edu)
 * @author David Erickson (daviderickson@cs.stanford.edu)
 *
 */
public enum OFType {
    HELLO               (0, OFHello.class, new Instantiable<OFMessage>() {
                            @Override
                            public OFMessage instantiate() {
                                return new OFHello();
                            }}),
    ERROR               (1, OFError.class, new Instantiable<OFMessage>() {
                            @Override
                            public OFMessage instantiate() {
                                return new OFError();
                            }}),
    ECHO_REQUEST        (2, OFEchoRequest.class, new Instantiable<OFMessage>() {
                            @Override
                            public OFMessage instantiate() {
                                return new OFEchoRequest();
                            }}),
    ECHO_REPLY          (3, OFEchoReply.class, new Instantiable<OFMessage>() {
                            @Override
                            public OFMessage instantiate() {
                                return new OFEchoReply();
                            }}),
    VENDOR              (4, OFVendor.class, new Instantiable<OFMessage>() {
                            @Override
                            public OFMessage instantiate() {
                                return new OFVendor();
                            }}),
    FEATURES_REQUEST    (5, OFFeaturesRequest.class, new Instantiable<OFMessage>() {
                            @Override
                            public OFMessage instantiate() {
                                return new OFFeaturesRequest();
                            }}),
    FEATURES_REPLY      (6, OFFeaturesReply.class, new Instantiable<OFMessage>() {
                            @Override
                            public OFMessage instantiate() {
                                return new OFFeaturesReply();
                            }}),
    GET_CONFIG_REQUEST  (7, OFGetConfigRequest.class, new Instantiable<OFMessage>() {
                            @Override
                            public OFMessage instantiate() {
                                return new OFGetConfigRequest();
                            }}),
    GET_CONFIG_REPLY    (8, OFGetConfigReply.class, new Instantiable<OFMessage>() {
                            @Override
                            public OFMessage instantiate() {
                                return new OFGetConfigReply();
                            }}),
    SET_CONFIG          (9, OFSetConfig.class, new Instantiable<OFMessage>() {
                            @Override
                            public OFMessage instantiate() {
                                return new OFSetConfig();
                            }}),
    PACKET_IN           (10, OFPacketIn.class, new Instantiable<OFMessage>() {
                            @Override
                            public OFMessage instantiate() {
                                return new OFPacketIn();
                            }}),
    FLOW_REMOVED        (11, OFFlowRemoved.class, new Instantiable<OFMessage>() {
                            @Override
                            public OFMessage instantiate() {
                                return new OFFlowRemoved();
                            }}),
    PORT_STATUS         (12, OFPortStatus.class, new Instantiable<OFMessage>() {
                            @Override
                            public OFMessage instantiate() {
                                return new OFPortStatus();
                            }}),
    PACKET_OUT          (13, OFPacketOut.class, new Instantiable<OFMessage>() {
                            @Override
                            public OFMessage instantiate() {
                                return new OFPacketOut();
                            }}),
    FLOW_MOD            (14, OFFlowMod.class, new Instantiable<OFMessage>() {
                            @Override
                            public OFMessage instantiate() {
                                return new OFFlowMod();
                            }}),
    PORT_MOD            (15, OFPortMod.class, new Instantiable<OFMessage>() {
                            @Override
                            public OFMessage instantiate() {
                                return new OFPortMod();
                            }}),
    STATS_REQUEST       (16, OFStatisticsRequest.class, new Instantiable<OFMessage>() {
                            @Override
                            public OFMessage instantiate() {
                                return new OFStatisticsRequest();
                            }}),
    STATS_REPLY         (17, OFStatisticsReply.class, new Instantiable<OFMessage>() {
                            @Override
                            public OFMessage instantiate() {
                                return new OFStatisticsReply();
                            }}),
    BARRIER_REQUEST     (18, OFBarrierRequest.class, new Instantiable<OFMessage>() {
                            @Override
                            public OFMessage instantiate() {
                                return new OFBarrierRequest();
                            }}),
    BARRIER_REPLY       (19, OFBarrierReply.class, new Instantiable<OFMessage>() {
                            @Override
                            public OFMessage instantiate() {
                                return new OFBarrierReply();
                            }}),
    QUEUE_GET_CONFIG_REQUEST    (20, OFQueueGetConfigRequest.class, new Instantiable<OFMessage>() {
                                    @Override
                                    public OFMessage instantiate() {
                                        return new OFQueueGetConfigRequest();
                                    }}),
    QUEUE_GET_CONFIG_REPLY      (21, OFQueueGetConfigReply.class, new Instantiable<OFMessage>() {
                                    @Override
                                    public OFMessage instantiate() {
                                        return new OFQueueGetConfigReply();
                                    }});

    static OFType[] mapping;

    protected Class<? extends OFMessage> clazz;
    protected Constructor<? extends OFMessage> constructor;
    protected Instantiable<OFMessage> instantiable;
    protected byte type;

    /**
     * Store some information about the OpenFlow type, including wire protocol
     * type number, length, and derived class
     *
     * @param type Wire protocol number associated with this OFType
     * @param clazz The Java class corresponding to this type of OpenFlow
     *              message
     * @param instantiator An Instantiator<OFMessage> implementation that creates an
     *          instance of the specified OFMessage
     */
    OFType(int type, Class<? extends OFMessage> clazz, Instantiable<OFMessage> instantiator) {
        this.type = (byte) type;
        this.clazz = clazz;
        this.instantiable = instantiator;
        try {
            this.constructor = clazz.getConstructor(new Class[]{});
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failure getting constructor for class: " + clazz, e);
        }
        OFType.addMapping(this.type, this);
    }

    /**
     * Adds a mapping from type value to OFType enum
     *
     * @param i OpenFlow wire protocol type
     * @param t type
     */
    static public void addMapping(byte i, OFType t) {
        if (mapping == null)
            mapping = new OFType[32];
        OFType.mapping[i] = t;
    }

    /**
     * Remove a mapping from type value to OFType enum
     *
     * @param i OpenFlow wire protocol type
     */
    static public void removeMapping(byte i) {
        OFType.mapping[i] = null;
    }

    /**
     * Given a wire protocol OpenFlow type number, return the OFType associated
     * with it
     *
     * @param i wire protocol number
     * @return OFType enum type
     */

    static public OFType valueOf(Byte i) {
        return OFType.mapping[i];
    }

    /**
     * @return Returns the wire protocol value corresponding to this OFType
     */
    public byte getTypeValue() {
        return this.type;
    }

    /**
     * @return return the OFMessage subclass corresponding to this OFType
     */
    public Class<? extends OFMessage> toClass() {
        return clazz;
    }

    /**
     * Returns the no-argument Constructor of the implementation class for
     * this OFType
     * @return the constructor
     */
    public Constructor<? extends OFMessage> getConstructor() {
        return constructor;
    }

    /**
     * Returns a new instance of the OFMessage represented by this OFType
     * @return the new object
     */
    public OFMessage newInstance() {
        return instantiable.instantiate();
    }

    /**
     * @return the instantiable
     */
    public Instantiable<OFMessage> getInstantiable() {
        return instantiable;
    }

    /**
     * @param instantiable the instantiable to set
     */
    public void setInstantiable(Instantiable<OFMessage> instantiable) {
        this.instantiable = instantiable;
    }
}
