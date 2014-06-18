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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFType;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.threadpool.IThreadPoolService;

/**
 * A Future object used to retrieve asynchronous OFMessage replies. Unregisters
 * and cancels itself by default after 60 seconds. This class is meant to be
 * sub-classed and proper behavior added to the handleReply method, and
 * termination of the Future to be handled in the isFinished method.
 *
 * @author David Erickson (daviderickson@cs.stanford.edu)
 */
public abstract class OFMessageFuture<V> implements Future<V> {

    protected IThreadPoolService threadPool;
    protected volatile boolean canceled;
    protected CountDownLatch latch;
    protected OFType responseType;
    protected volatile V result;
    protected IOFSwitch sw;
    protected Runnable timeoutTimer;
    protected int transactionId;
    protected static final long DEFAULT_TIMEOUT = 60;
    protected static final TimeUnit DEFAULT_TIMEOUT_UNIT = TimeUnit.SECONDS;

    public OFMessageFuture(IThreadPoolService tp,
            IOFSwitch sw, OFType responseType, int transactionId) {
        this(tp, sw, responseType, transactionId,
                 DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNIT);
    }

    public OFMessageFuture(IThreadPoolService tp,
            IOFSwitch sw, OFType responseType, int transactionId, long timeout, TimeUnit unit) {
        this.threadPool = tp;
        this.canceled = false;
        this.latch = new CountDownLatch(1);
        this.responseType = responseType;
        this.sw = sw;
        this.transactionId = transactionId;

        final OFMessageFuture<V> future = this;
        timeoutTimer = new Runnable() {
            @Override
            public void run() {
                if (timeoutTimer == this)
                    future.cancel(true);
            }
        };
        threadPool.getScheduledExecutor().schedule(timeoutTimer, timeout, unit);
    }

    protected void unRegister() {
        this.timeoutTimer = null;
    }


    // TODO: msg should be generic!
    public void deliverFuture(IOFSwitch sw, OFMessage msg) {
        if (transactionId == msg.getXid()) {
            handleReply(sw, msg);
            if (isFinished()) {
                unRegister();
                this.latch.countDown();
            }
        }
    }

    /**
     * Used to handle the specific expected message this Future was reigstered
     * for, the specified msg parameter is guaranteed to match the type and
     * transaction id specified.
     * @param sw
     * @param msg
     * @return
     */
    protected abstract void handleReply(IOFSwitch sw, OFMessage msg);

    /**
     * Called directly after handleReply, subclasses implement this method to
     * indicate when the future can deregister itself from receiving future
     * messages, and when it is safe to return the results to any waiting
     * threads.
     * @return when this Future has completed its work
     */
    protected abstract boolean isFinished();

    /* (non-Javadoc)
     * @see java.util.concurrent.Future#cancel(boolean)
     */
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (isDone()) {
            return false;
        } else {
            unRegister();
            canceled = true;
            this.latch.countDown();
            return !isDone();
        }
    }

    /* (non-Javadoc)
     * @see java.util.concurrent.Future#isCancelled()
     */
    @Override
    public boolean isCancelled() {
        return canceled;
    }

    /* (non-Javadoc)
     * @see java.util.concurrent.Future#isDone()
     */
    @Override
    public boolean isDone() {
        return this.latch.getCount() == 0;
    }

    /* (non-Javadoc)
     * @see java.util.concurrent.Future#get()
     */
    @Override
    public V get() throws InterruptedException, ExecutionException {
        this.latch.await();
        return result;
    }

    /* (non-Javadoc)
     * @see java.util.concurrent.Future#get(long, java.util.concurrent.TimeUnit)
     */
    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException,
            ExecutionException, TimeoutException {
        this.latch.await(timeout, unit);
        return result;
    }

    public int getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(int transactionId) {
        this.transactionId = transactionId;
    }
}
