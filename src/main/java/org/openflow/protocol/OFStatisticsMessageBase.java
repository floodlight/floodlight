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

import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.factory.OFStatisticsFactory;
import org.openflow.protocol.factory.OFStatisticsFactoryAware;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;


/**
 * Base class for statistics requests/replies
 *
 * @author David Erickson (daviderickson@cs.stanford.edu) - Mar 27, 2010
 */
public abstract class OFStatisticsMessageBase extends OFMessage implements
        OFStatisticsFactoryAware {
    public static int MINIMUM_LENGTH = 12;

    protected OFStatisticsFactory statisticsFactory;
    protected OFStatisticsType statisticType;
    protected short flags;
    protected List<OFStatistics> statistics;

    /**
     * @return the statisticType
     */
    public OFStatisticsType getStatisticType() {
        return statisticType;
    }

    /**
     * @param statisticType the statisticType to set
     */
    public void setStatisticType(OFStatisticsType statisticType) {
        this.statisticType = statisticType;
    }

    /**
     * @return the flags
     */
    public short getFlags() {
        return flags;
    }

    /**
     * @param flags the flags to set
     */
    public void setFlags(short flags) {
        this.flags = flags;
    }

    /**
     * @return the statistics
     */
    public List<OFStatistics> getStatistics() {
        return statistics;
    }

    /**
     * @param statistics the statistics to set
     */
    public void setStatistics(List<OFStatistics> statistics) {
        this.statistics = statistics;
    }

    @Override
    public void setStatisticsFactory(OFStatisticsFactory statisticsFactory) {
        this.statisticsFactory = statisticsFactory;
    }

    @Override
    public void readFrom(ChannelBuffer data) {
        super.readFrom(data);
        this.statisticType = OFStatisticsType.valueOf(data.readShort(), this
                .getType());
        this.flags = data.readShort();
        if (this.statisticsFactory == null)
            throw new RuntimeException("OFStatisticsFactory not set");
        this.statistics = statisticsFactory.parseStatistics(this.getType(),
                this.statisticType, data, super.getLengthU() - MINIMUM_LENGTH);
    }

    @Override
    public void writeTo(ChannelBuffer data) {
        super.writeTo(data);
        data.writeShort(this.statisticType.getTypeValue());
        data.writeShort(this.flags);
        if (this.statistics != null) {
            for (OFStatistics statistic : this.statistics) {
                statistic.writeTo(data);
            }
        }
    }

    @Override
    public int hashCode() {
        final int prime = 317;
        int result = super.hashCode();
        result = prime * result + flags;
        result = prime * result
                + ((statisticType == null) ? 0 : statisticType.hashCode());
        result = prime * result
                + ((statistics == null) ? 0 : statistics.hashCode());
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
        if (!(obj instanceof OFStatisticsMessageBase)) {
            return false;
        }
        OFStatisticsMessageBase other = (OFStatisticsMessageBase) obj;
        if (flags != other.flags) {
            return false;
        }
        if (statisticType == null) {
            if (other.statisticType != null) {
                return false;
            }
        } else if (!statisticType.equals(other.statisticType)) {
            return false;
        }
        if (statistics == null) {
            if (other.statistics != null) {
                return false;
            }
        } else if (!statistics.equals(other.statistics)) {
            return false;
        }
        return true;
    }
}
