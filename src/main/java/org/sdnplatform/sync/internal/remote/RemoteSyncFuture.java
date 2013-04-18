package org.sdnplatform.sync.internal.remote;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class RemoteSyncFuture implements Future<SyncReply> {

    private final int xid;
    private volatile SyncReply reply = null;
    private Object notify = new Object();
    
    public RemoteSyncFuture(int xid) {
        super();
        this.xid = xid;
    }

    // **********************
    // Future<SyncReply>
    // **********************
    
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public SyncReply get() throws InterruptedException,
                               ExecutionException {
        if (reply != null) return reply;
        synchronized (notify) {
            while (reply == null)
                notify.wait();
        }
        return reply;
    }

    @Override
    public SyncReply
            get(long timeout, TimeUnit unit) throws InterruptedException,
                                            ExecutionException,
                                            TimeoutException {
        if (reply != null) return reply;
        synchronized (notify) {
            notify.wait(TimeUnit.MILLISECONDS.convert(timeout, unit));
        }
        if (reply == null) throw new TimeoutException();
        return reply;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return (reply != null);
    }

    // ****************
    // RemoteSyncFuture
    // ****************
    
    /**
     * Get the xid for this message
     * @return
     */
    public int getXid() {
        return xid;
    }
    
    /**
     * Set the reply message
     * @param reply
     */
    public void setReply(SyncReply reply) {
        synchronized (notify) {
            this.reply = reply;
            notify.notifyAll();
        }
    }
}
