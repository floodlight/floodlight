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

package net.floodlightcontroller.core.internal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.threadpool.IThreadPoolService;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFStatisticsReply;
import org.openflow.protocol.OFType;
import org.openflow.protocol.statistics.OFStatistics;

/**
 * A concrete implementation that handles asynchronously receiving OFStatistics
 * 
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public class OFStatisticsFuture extends
        OFMessageFuture<List<OFStatistics>> {

    protected volatile boolean finished;

    public OFStatisticsFuture(IThreadPoolService tp,
            IOFSwitch sw, int transactionId) {
        super(tp, sw, OFType.STATS_REPLY, transactionId);
        init();
    }

    public OFStatisticsFuture(IThreadPoolService tp,
            IOFSwitch sw, int transactionId, long timeout, TimeUnit unit) {
        super(tp, sw, OFType.STATS_REPLY, transactionId, timeout, unit);
        init();
    }

    private void init() {
        this.finished = false;
        this.result = new CopyOnWriteArrayList<OFStatistics>();
    }

    @Override
    protected void handleReply(IOFSwitch sw, OFMessage msg) {
        OFStatisticsReply sr = (OFStatisticsReply) msg;
        synchronized (this.result) {
            this.result.addAll(sr.getStatistics());
            if ((sr.getFlags() & 0x1) == 0) {
                this.finished = true;
            }
        }
    }

    @Override
    protected boolean isFinished() {
        return finished;
    }
    
    @Override
    protected void unRegister() {
        super.unRegister();
        sw.cancelStatisticsReply(transactionId);
    }
}
