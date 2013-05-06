package net.floodlightcontroller.core.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.test.MockThreadPoolService;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.openflow.protocol.OFStatisticsReply;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;

import static org.easymock.EasyMock.*;

public class OFStatisticsFutureTest {
    private MockThreadPoolService tp;

    @Before
    public void setUp() {
        tp = new MockThreadPoolService();
    }

    private OFStatisticsReply getStatisticsReply(int transactionId,
                                                   int count, boolean moreReplies) {
        OFStatisticsReply sr = new OFStatisticsReply();
        sr.setXid(transactionId);
        sr.setStatisticType(OFStatisticsType.FLOW);
        List<OFStatistics> statistics = new ArrayList<OFStatistics>();
        for (int i = 0; i < count; ++i) {
            statistics.add(new OFFlowStatisticsReply());
        }
        sr.setStatistics(statistics);
        if (moreReplies)
            sr.setFlags((short) 1);
        return sr;
    }

    public class FutureFetcher<E> implements Runnable {
        public E value;
        public Future<E> future;

        public FutureFetcher(Future<E> future) {
            this.future = future;
        }

        @Override
        public void run() {
            try {
                value = future.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * @return the value
         */
        public E getValue() {
            return value;
        }

        /**
         * @return the future
         */
        public Future<E> getFuture() {
            return future;
        }
    }

    /**
    *
    * @throws Exception
    */
   @Test
   public void testOFStatisticsFuture() throws Exception {
       // Test for a single stats reply
       IOFSwitch sw = createMock(IOFSwitch.class);
       sw.cancelStatisticsReply(1);
       OFStatisticsFuture sf = new OFStatisticsFuture(tp, sw, 1);

       replay(sw);
       List<OFStatistics> stats;
       FutureFetcher<List<OFStatistics>> ff = new FutureFetcher<List<OFStatistics>>(sf);
       Thread t = new Thread(ff);
       t.start();
       sf.deliverFuture(sw, getStatisticsReply(1, 10, false));

       t.join();
       stats = ff.getValue();
       verify(sw);
       assertEquals(10, stats.size());

       // Test multiple stats replies
       reset(sw);
       sw.cancelStatisticsReply(1);

       sf = new OFStatisticsFuture(tp, sw, 1);

       replay(sw);
       ff = new FutureFetcher<List<OFStatistics>>(sf);
       t = new Thread(ff);
       t.start();
       sf.deliverFuture(sw, getStatisticsReply(1, 10, true));
       sf.deliverFuture(sw, getStatisticsReply(1, 5, false));
       t.join();

       stats = sf.get();
       verify(sw);
       assertEquals(15, stats.size());

       // Test cancellation
       reset(sw);
       sw.cancelStatisticsReply(1);
       sf = new OFStatisticsFuture(tp, sw, 1);

       replay(sw);
       ff = new FutureFetcher<List<OFStatistics>>(sf);
       t = new Thread(ff);
       t.start();
       sf.cancel(true);
       t.join();

       stats = sf.get();
       verify(sw);
       assertEquals(0, stats.size());

       // Test self timeout
       reset(sw);
       sw.cancelStatisticsReply(1);
       sf = new OFStatisticsFuture(tp, sw, 1, 75, TimeUnit.MILLISECONDS);

       replay(sw);
       ff = new FutureFetcher<List<OFStatistics>>(sf);
       t = new Thread(ff);
       t.start();
       t.join(2000);

       stats = sf.get();
       verify(sw);
       assertEquals(0, stats.size());
   }

}
