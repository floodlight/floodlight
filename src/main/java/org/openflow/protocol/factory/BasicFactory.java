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

package org.openflow.protocol.factory;

import java.util.ArrayList;
import java.util.List;

import org.jboss.netty.buffer.ChannelBuffer;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionType;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.protocol.statistics.OFVendorStatistics;


/**
 * A basic OpenFlow factory that supports naive creation of both Messages and
 * Actions.
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 * @author Rob Sherwood (rob.sherwood@stanford.edu)
 *
 */
public class BasicFactory implements OFMessageFactory, OFActionFactory,
        OFStatisticsFactory {
    @Override
    public OFMessage getMessage(OFType t) {
        return t.newInstance();
    }

    @Override
    public OFMessage parseMessage(ChannelBuffer data) throws MessageParseException {
        try {
            OFMessage demux = new OFMessage();
            OFMessage ofm = null;

            if (data.readableBytes() < OFMessage.MINIMUM_LENGTH)
                return ofm;

            data.markReaderIndex();
            demux.readFrom(data);
            data.resetReaderIndex();

            if (demux.getLengthU() > data.readableBytes())
                return ofm;

            ofm = getMessage(demux.getType());
            if (ofm == null)
                return null;

            if (ofm instanceof OFActionFactoryAware) {
                ((OFActionFactoryAware)ofm).setActionFactory(this);
            }
            if (ofm instanceof OFMessageFactoryAware) {
                ((OFMessageFactoryAware)ofm).setMessageFactory(this);
            }
            if (ofm instanceof OFStatisticsFactoryAware) {
                ((OFStatisticsFactoryAware)ofm).setStatisticsFactory(this);
            }
            ofm.readFrom(data);
            if (OFMessage.class.equals(ofm.getClass())) {
                // advance the position for un-implemented messages
                data.readerIndex(data.readerIndex()+(ofm.getLengthU() -
                        OFMessage.MINIMUM_LENGTH));
            }

            return ofm;
        } catch (Exception e) {
            throw new MessageParseException(e);
        }
    }

    @Override
    public OFAction getAction(OFActionType t) {
        return t.newInstance();
    }

    @Override
    public List<OFAction> parseActions(ChannelBuffer data, int length) {
        return parseActions(data, length, 0);
    }

    @Override
    public List<OFAction> parseActions(ChannelBuffer data, int length, int limit) {
        List<OFAction> results = new ArrayList<OFAction>();
        OFAction demux = new OFAction();
        OFAction ofa;
        int end = data.readerIndex() + length;

        while (limit == 0 || results.size() <= limit) {
            if ((data.readableBytes() < OFAction.MINIMUM_LENGTH ||
                (data.readerIndex() + OFAction.MINIMUM_LENGTH) > end))
                return results;

            data.markReaderIndex();
            demux.readFrom(data);
            data.resetReaderIndex();

            if ((demux.getLengthU() > data.readableBytes() ||
                (data.readerIndex() + demux.getLengthU()) > end))
                return results;

            ofa = getAction(demux.getType());
            ofa.readFrom(data);
            if (OFAction.class.equals(ofa.getClass())) {
                // advance the position for un-implemented messages
                data.readerIndex(data.readerIndex()+(ofa.getLengthU() -
                        OFAction.MINIMUM_LENGTH));
            }
            results.add(ofa);
        }

        return results;
    }

    @Override
    public OFActionFactory getActionFactory() {
        return this;
    }

    @Override
    public OFStatistics getStatistics(OFType t, OFStatisticsType st) {
        return st.newInstance(t);
    }

    @Override
    public List<OFStatistics> parseStatistics(OFType t, OFStatisticsType st,
                                              ChannelBuffer data, int length) {
        return parseStatistics(t, st, data, length, 0);
    }

    /**
     * @param t
     *            OFMessage type: should be one of stats_request or stats_reply
     * @param st
     *            statistics type of this message, e.g., DESC, TABLE
     * @param data
     *            buffer to read from
     * @param length
     *            length of statistics
     * @param limit
     *            number of statistics to grab; 0 == all
     * 
     * @return list of statistics
     */

    @Override
    public List<OFStatistics> parseStatistics(OFType t, OFStatisticsType st,
            ChannelBuffer data, int length, int limit) {
        List<OFStatistics> results = new ArrayList<OFStatistics>();
        OFStatistics statistics = getStatistics(t, st);

        int start = data.readerIndex();
        int count = 0;

        while (limit == 0 || results.size() <= limit) {
            // TODO Create a separate MUX/DEMUX path for vendor stats
            if (statistics instanceof OFVendorStatistics)
                ((OFVendorStatistics)statistics).setLength(length);

            /**
             * can't use data.remaining() here, b/c there could be other data
             * buffered past this message
             */
            if ((length - count) >= statistics.getLength()) {
                if (statistics instanceof OFActionFactoryAware)
                    ((OFActionFactoryAware)statistics).setActionFactory(this);
                statistics.readFrom(data);
                results.add(statistics);
                count += statistics.getLength();
                statistics = getStatistics(t, st);
            } else {
                if (count < length) {
                    /**
                     * Nasty case: partial/incomplete statistic found even
                     * though we have a full message. Found when NOX sent
                     * agg_stats request with wrong agg statistics length (52
                     * instead of 56)
                     * 
                     * just throw the rest away, or we will break framing
                     */
                    data.readerIndex(start + length);
                }
                return results;
            }
        }
        return results; // empty; no statistics at all
    }
}
