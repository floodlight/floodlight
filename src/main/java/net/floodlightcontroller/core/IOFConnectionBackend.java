package net.floodlightcontroller.core;

import net.floodlightcontroller.core.internal.IOFConnectionListener;

public interface IOFConnectionBackend extends IOFConnection {
    /**
     * Disconnect the channel
     */
    void disconnect();

    /**
     * Cancel all pending request
     */
    void cancelAllPendingRequests();

    /** @return whether the output stream associated with this connection
     *  is currently writeable (for throttling)
     */
    boolean isWritable();

    /** set the message/closing listener for this connection */
    void setListener(IOFConnectionListener listener);
}
