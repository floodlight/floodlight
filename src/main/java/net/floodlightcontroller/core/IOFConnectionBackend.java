package net.floodlightcontroller.core;

import org.projectfloodlight.openflow.types.U64;

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
    
    /**
     * Update the present latency between the switch and
     * the controller. The latency should be in milliseconds
     * and should be one-way. The caller must convert all
     * round-trip values to one-way prior to invoking this
     * function.
     * 
     * The old link latency being updated will retain X%
     * of the value, while the new link latency will attribute
     * (100-X)%. This should allow new network configurations to
     * quickly overtake old ones but will still allow
     * outlier values to be absorbed.
     * 
     * @param latency
     */
    public void updateLatency(U64 latency);
}
