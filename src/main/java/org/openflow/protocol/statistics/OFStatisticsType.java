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

package org.openflow.protocol.statistics;

import java.lang.reflect.Constructor;

import org.openflow.protocol.Instantiable;
import org.openflow.protocol.OFType;

public enum OFStatisticsType {
    DESC        (0, OFDescriptionStatistics.class, OFDescriptionStatistics.class,
                    new Instantiable<OFStatistics>() {
                        @Override
                        public OFStatistics instantiate() {
                            return new OFDescriptionStatistics();
                        }
                    },
                    new Instantiable<OFStatistics>() {
                        @Override
                        public OFStatistics instantiate() {
                            return new OFDescriptionStatistics();
                        }
                    }),
    FLOW       (1, OFFlowStatisticsRequest.class, OFFlowStatisticsReply.class,
                    new Instantiable<OFStatistics>() {
                        @Override
                        public OFStatistics instantiate() {
                            return new OFFlowStatisticsRequest();
                        }
                    },
                    new Instantiable<OFStatistics>() {
                        @Override
                        public OFStatistics instantiate() {
                            return new OFFlowStatisticsReply();
                        }
                    }),
    AGGREGATE  (2, OFAggregateStatisticsRequest.class, OFAggregateStatisticsReply.class,
                    new Instantiable<OFStatistics>() {
                        @Override
                        public OFStatistics instantiate() {
                            return new OFAggregateStatisticsRequest();
                        }
                    },
                    new Instantiable<OFStatistics>() {
                        @Override
                        public OFStatistics instantiate() {
                            return new OFAggregateStatisticsReply();
                        }
                    }),
    TABLE      (3, OFTableStatistics.class, OFTableStatistics.class,
                    new Instantiable<OFStatistics>() {
                        @Override
                        public OFStatistics instantiate() {
                            return new OFTableStatistics();
                        }
                    },
                    new Instantiable<OFStatistics>() {
                        @Override
                        public OFStatistics instantiate() {
                            return new OFTableStatistics();
                        }
                    }),
    PORT       (4, OFPortStatisticsRequest.class, OFPortStatisticsReply.class,
                    new Instantiable<OFStatistics>() {
                        @Override
                        public OFStatistics instantiate() {
                            return new OFPortStatisticsRequest();
                        }
                    },
                    new Instantiable<OFStatistics>() {
                        @Override
                        public OFStatistics instantiate() {
                            return new OFPortStatisticsReply();
                        }
                    }),
    QUEUE      (5, OFQueueStatisticsRequest.class, OFQueueStatisticsReply.class,
                    new Instantiable<OFStatistics>() {
                        @Override
                        public OFStatistics instantiate() {
                            return new OFQueueStatisticsRequest();
                        }
                    },
                    new Instantiable<OFStatistics>() {
                        @Override
                        public OFStatistics instantiate() {
                            return new OFQueueStatisticsReply();
                        }
                    }),
    VENDOR     (0xffff, OFVendorStatistics.class, OFVendorStatistics.class,
                    new Instantiable<OFStatistics>() {
                        @Override
                        public OFStatistics instantiate() {
                            return new OFVendorStatistics();
                        }
                    },
                    new Instantiable<OFStatistics>() {
                        @Override
                        public OFStatistics instantiate() {
                            return new OFVendorStatistics();
                        }
                    });

    static OFStatisticsType[] requestMapping;
    static OFStatisticsType[] replyMapping;

    protected Class<? extends OFStatistics> requestClass;
    protected Constructor<? extends OFStatistics> requestConstructor;
    protected Instantiable<OFStatistics> requestInstantiable;
    protected Class<? extends OFStatistics> replyClass;
    protected Constructor<? extends OFStatistics> replyConstructor;
    protected Instantiable<OFStatistics> replyInstantiable;
    protected short type;

    /**
     * Store some information about the OpenFlow Statistic type, including wire
     * protocol type number, and derived class
     *
     * @param type Wire protocol number associated with this OFStatisticsType
     * @param requestClass The Statistics Java class to return when the
     *                     containing OFType is STATS_REQUEST
     * @param replyClass   The Statistics Java class to return when the
     *                     containing OFType is STATS_REPLY
     */
    OFStatisticsType(int type, Class<? extends OFStatistics> requestClass,
            Class<? extends OFStatistics> replyClass,
            Instantiable<OFStatistics> requestInstantiable,
            Instantiable<OFStatistics> replyInstantiable) {
        this.type = (short) type;
        this.requestClass = requestClass;
        try {
            this.requestConstructor = requestClass.getConstructor(new Class[]{});
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failure getting constructor for class: " + requestClass, e);
        }

        this.replyClass = replyClass;
        try {
            this.replyConstructor = replyClass.getConstructor(new Class[]{});
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failure getting constructor for class: " + replyClass, e);
        }
        this.requestInstantiable = requestInstantiable;
        this.replyInstantiable = replyInstantiable;
        OFStatisticsType.addMapping(this.type, OFType.STATS_REQUEST, this);
        OFStatisticsType.addMapping(this.type, OFType.STATS_REPLY, this);
    }

    /**
     * Adds a mapping from type value to OFStatisticsType enum
     *
     * @param i OpenFlow wire protocol type
     * @param t type of containing OFMessage, only accepts STATS_REQUEST or
     *          STATS_REPLY
     * @param st type
     */
    static public void addMapping(short i, OFType t, OFStatisticsType st) {
        if (i < 0)
            i = (short) (16+i);
        if (t == OFType.STATS_REQUEST) {
            if (requestMapping == null)
                requestMapping = new OFStatisticsType[16];
            OFStatisticsType.requestMapping[i] = st;
        } else if (t == OFType.STATS_REPLY){
            if (replyMapping == null)
                replyMapping = new OFStatisticsType[16];
            OFStatisticsType.replyMapping[i] = st;
        } else {
            throw new RuntimeException(t.toString() + " is an invalid OFType");
        }
    }

    /**
     * Remove a mapping from type value to OFStatisticsType enum
     *
     * @param i OpenFlow wire protocol type
     * @param t type of containing OFMessage, only accepts STATS_REQUEST or
     *          STATS_REPLY
     */
    static public void removeMapping(short i, OFType t) {
        if (i < 0)
            i = (short) (16+i);
        if (t == OFType.STATS_REQUEST) {
            requestMapping[i] = null;
        } else if (t == OFType.STATS_REPLY){
            replyMapping[i] = null;
        } else {
            throw new RuntimeException(t.toString() + " is an invalid OFType");
        }
    }

    /**
     * Given a wire protocol OpenFlow type number, return the OFStatisticsType
     * associated with it
     *
     * @param i wire protocol number
     * @param t type of containing OFMessage, only accepts STATS_REQUEST or
     *          STATS_REPLY
     * @return OFStatisticsType enum type
     */
    static public OFStatisticsType valueOf(short i, OFType t) {
        if (i < 0)
            i = (short) (16+i);
        if (t == OFType.STATS_REQUEST) {
            return requestMapping[i];
        } else if (t == OFType.STATS_REPLY){
            return replyMapping[i];
        } else {
            throw new RuntimeException(t.toString() + " is an invalid OFType");
        }
    }

    /**
     * @return Returns the wire protocol value corresponding to this
     * OFStatisticsType
     */
    public short getTypeValue() {
        return this.type;
    }

    /**
     * @param t type of containing OFMessage, only accepts STATS_REQUEST or
     *          STATS_REPLY
     * @return return the OFMessage subclass corresponding to this
     *                OFStatisticsType
     */
    public Class<? extends OFStatistics> toClass(OFType t) {
        if (t == OFType.STATS_REQUEST) {
            return requestClass;
        } else if (t == OFType.STATS_REPLY){
            return replyClass;
        } else {
            throw new RuntimeException(t.toString() + " is an invalid OFType");
        }
    }

    /**
     * Returns the no-argument Constructor of the implementation class for
     * this OFStatisticsType, either request or reply based on the supplied
     * OFType
     *
     * @param t
     * @return
     */
    public Constructor<? extends OFStatistics> getConstructor(OFType t) {
        if (t == OFType.STATS_REQUEST) {
            return requestConstructor;
        } else if (t == OFType.STATS_REPLY) {
            return replyConstructor;
        } else {
            throw new RuntimeException(t.toString() + " is an invalid OFType");
        }
    }

    /**
     * @return the requestInstantiable
     */
    public Instantiable<OFStatistics> getRequestInstantiable() {
        return requestInstantiable;
    }

    /**
     * @param requestInstantiable the requestInstantiable to set
     */
    public void setRequestInstantiable(
            Instantiable<OFStatistics> requestInstantiable) {
        this.requestInstantiable = requestInstantiable;
    }

    /**
     * @return the replyInstantiable
     */
    public Instantiable<OFStatistics> getReplyInstantiable() {
        return replyInstantiable;
    }

    /**
     * @param replyInstantiable the replyInstantiable to set
     */
    public void setReplyInstantiable(Instantiable<OFStatistics> replyInstantiable) {
        this.replyInstantiable = replyInstantiable;
    }

    /**
     * Returns a new instance of the implementation class for
     * this OFStatisticsType, either request or reply based on the supplied
     * OFType
     *
     * @param t
     * @return
     */
    public OFStatistics newInstance(OFType t) {
        if (t == OFType.STATS_REQUEST) {
            return requestInstantiable.instantiate();
        } else if (t == OFType.STATS_REPLY) {
            return replyInstantiable.instantiate();
        } else {
            throw new RuntimeException(t.toString() + " is an invalid OFType");
        }
    }
}
