package net.floodlightcontroller.packetstreamer;

import net.floodlightcontroller.packetstreamer.thrift.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PacketStreamerHandler implements PacketStreamer.Iface {

    protected class SessionQueue {
        protected BlockingQueue<ByteBuffer> pQueue;
        protected boolean terminated;

        public SessionQueue() {
            this.pQueue = new LinkedBlockingQueue<ByteBuffer>();
            this.terminated = false;
        }

        public boolean isTerminated() {
            return this.terminated;
        }

        public void setTerminated(boolean terminated) {
            this.terminated = terminated;
        }

        public BlockingQueue<ByteBuffer> getQueue() {
            return this.pQueue;
        }
    }
    
    protected static Logger log = LoggerFactory.getLogger(PacketStreamerServer.class);
    protected Map<String, SessionQueue> msgQueues;

    public PacketStreamerHandler() {
        this.msgQueues = new ConcurrentHashMap<String, SessionQueue>();
    }

    @Override
    public List<ByteBuffer> getPackets(String sessionid)
            throws org.apache.thrift.TException {
        List<ByteBuffer> packets = new ArrayList<ByteBuffer>();

        while (!msgQueues.containsKey(sessionid)) {
            log.debug("Queue for session {} doesn't exist yet.", sessionid);
            try {
                Thread.sleep(100);    // Wait 100 ms to check again.
            } catch (InterruptedException e) {
                log.error(e.toString());
            }
        }

        SessionQueue pQueue = msgQueues.get(sessionid);
        BlockingQueue<ByteBuffer> queue = pQueue.getQueue();
        // Block if queue is empty
        try {
            packets.add(queue.take());
            queue.drainTo(packets);
        } catch (InterruptedException e) {
            log.error(e.toString());
        }

        return packets;
    }

    @Override
    public int pushMessageSync(Message msg)
            throws org.apache.thrift.TException {

        if (msg == null) {
            log.error("Error, pushMessageSync: empty message ");
            return 0;
        }

        List<String> sessionids = msg.getSessionIDs();
        for (String sid : sessionids) {
            SessionQueue pQueue = null;

            if (!msgQueues.containsKey(sid)) {
                pQueue = new SessionQueue();
                msgQueues.put(sid, pQueue);
            } else {
                pQueue = msgQueues.get(sid);
            }

            log.debug("pushMessageSync: SessionId: " + sid + " Receive a message, " + msg.toString() + "\n");
            ByteBuffer bb = ByteBuffer.wrap(msg.getPacket().getData());
            //ByteBuffer dst = ByteBuffer.wrap(msg.getPacket().toString().getBytes());
            BlockingQueue<ByteBuffer> queue = pQueue.getQueue();
            if (queue != null) {
                if (!queue.offer(bb)) {
                    log.error("Failed to queue message for session: " + sid);
                } else {
                    log.debug("insert a message to session: " + sid);
                }
            } else {
                log.error("queue for session {} is null", sid);
            }
        }

        return 1;
    }

    @Override
    public void pushMessageAsync(Message msg)
            throws org.apache.thrift.TException {
        pushMessageSync(msg);
        return;
    }

    @Override
    public void terminateSession(String sid)
            throws org.apache.thrift.TException {
        if (!msgQueues.containsKey(sid)) {
            return;
        }

        SessionQueue pQueue = msgQueues.get(sid);

        log.debug("terminateSession: SessionId: " + sid + "\n");
        String data = "FilterTimeout";
        ByteBuffer bb = ByteBuffer.wrap(data.getBytes());
        BlockingQueue<ByteBuffer> queue = pQueue.getQueue();
        if (queue != null) {
            if (!queue.offer(bb)) {
                log.error("Failed to queue message for session: " + sid);
            }
            msgQueues.remove(sid);
        } else {
            log.error("queue for session {} is null", sid);
        }
    }
}

