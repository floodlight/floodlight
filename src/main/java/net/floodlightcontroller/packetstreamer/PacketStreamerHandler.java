/**
 *    Copyright 2013, Big Switch Networks, Inc.
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

package net.floodlightcontroller.packetstreamer;

import net.floodlightcontroller.core.annotations.LogMessageCategory;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.annotations.LogMessageDocs;
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

/**
 * The PacketStreamer handler class that implements the service APIs.
 */
@LogMessageCategory("OpenFlow Message Tracing")
public class PacketStreamerHandler implements PacketStreamer.Iface {

	/**
	 * The queue wrapper class that contains the queue for the streamed packets.
	 */
    protected class SessionQueue {
        protected BlockingQueue<ByteBuffer> pQueue;

        /**
         * The queue wrapper constructor
         */
        public SessionQueue() {
            this.pQueue = new LinkedBlockingQueue<ByteBuffer>();
        }

        /**
         * The access method to get to the internal queue.
         */
        public BlockingQueue<ByteBuffer> getQueue() {
            return this.pQueue;
        }
    }
    
    /**
     * The class logger object
     */
    protected static Logger log = 
            LoggerFactory.getLogger(PacketStreamerServer.class);
    
    /**
     * A sessionId-to-queue mapping
     */
    protected Map<String, SessionQueue> msgQueues;

    /**
     * The handler's constructor
     */
    public PacketStreamerHandler() {
        this.msgQueues = new ConcurrentHashMap<String, SessionQueue>();
    }

    /**
     * The implementation for getPackets() function.
     * This is a blocking API.
     * 
     * @param sessionid
     * @return A list of packets associated with the session
     */
    @Override
    @LogMessageDocs({
        @LogMessageDoc(level="ERROR",
                message="Interrupted while waiting for session start",
                explanation="The thread was interrupted waiting " +
                     "for the packet streamer session to start",
                recommendation=LogMessageDoc.CHECK_CONTROLLER),
        @LogMessageDoc(level="ERROR",
                message="Interrupted while waiting for packets",
                explanation="The thread was interrupted waiting " +
                        "for packets",
                recommendation=LogMessageDoc.CHECK_CONTROLLER)
    })
    public List<ByteBuffer> getPackets(String sessionid)
            throws org.apache.thrift.TException {
        List<ByteBuffer> packets = new ArrayList<ByteBuffer>();
        int count = 0;
        
        while (!msgQueues.containsKey(sessionid) && count++ < 100) {
            log.debug("Queue for session {} doesn't exist yet.", sessionid);
            try {
                Thread.sleep(100);    // Wait 100 ms to check again.
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for session start");
            }
        }

        if (count < 100) {
	        SessionQueue pQueue = msgQueues.get(sessionid);
	        BlockingQueue<ByteBuffer> queue = pQueue.getQueue();
	        // Block if queue is empty
	        try {
	            packets.add(queue.take());
	            queue.drainTo(packets);
	        } catch (InterruptedException e) {
	            log.error("Interrupted while waiting for packets");
	        }
        }

        return packets;
    }

    /**
     * The implementation for pushMessageSync() function.
     * 
     * @param msg
     * @return 1 for success, 0 for failure
     * @throws TException
     */
    @Override
    @LogMessageDocs({
        @LogMessageDoc(level="ERROR",
                message="Could not push empty message",
                explanation="An empty message was sent to the packet streamer",
                recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG),
        @LogMessageDoc(level="ERROR",
                message="queue for session {sessionId} is null",
                explanation="The queue for the packet streamer session " +
                		"is missing",
                recommendation=LogMessageDoc.REPORT_CONTROLLER_BUG)
    })

    public int pushMessageSync(Message msg)
            throws org.apache.thrift.TException {

        if (msg == null) {
            log.error("Could not push empty message");
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

            log.debug("pushMessageSync: SessionId: " + sid + 
                      " Receive a message, " + msg.toString() + "\n");
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

    /**
     * The implementation for pushMessageAsync() function.
     * 
     * @param msg
     * @throws TException
     */
    @Override
    public void pushMessageAsync(Message msg)
            throws org.apache.thrift.TException {
        pushMessageSync(msg);
        return;
    }

    /**
     * The implementation for terminateSession() function.
     * It removes the session to queue association.
     * @param sessionid
     * @throws TException
     */
    @Override
    public void terminateSession(String sessionid)
            throws org.apache.thrift.TException {
        if (!msgQueues.containsKey(sessionid)) {
            return;
        }

        SessionQueue pQueue = msgQueues.get(sessionid);

        log.debug("terminateSession: SessionId: " + sessionid + "\n");
        String data = "FilterTimeout";
        ByteBuffer bb = ByteBuffer.wrap(data.getBytes());
        BlockingQueue<ByteBuffer> queue = pQueue.getQueue();
        if (queue != null) {
            if (!queue.offer(bb)) {
                log.error("Failed to queue message for session: " + sessionid);
            }
            msgQueues.remove(sessionid);
        } else {
            log.error("queue for session {} is null", sessionid);
        }
    }
}

