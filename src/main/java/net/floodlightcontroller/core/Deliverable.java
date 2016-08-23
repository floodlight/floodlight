package net.floodlightcontroller.core;

import org.projectfloodlight.openflow.protocol.OFMessage;

/**
 * abstracts the 'back side' of a Future that is being listened on, i.e., an
 * object that receives a result or an error of the computaton once it is ready.
 * A deliverable can accept multiple computation results, indicated by the
 * return value of deliver.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 * @param <T>
 *            type of the result of the computation
 */
public interface Deliverable<T> {
    public static enum Status {
        DONE,
        CONTINUE
    }

    /**
     * deliver the result after a successful computation has completed
     *
     * @param msg
     *            result
     * @return whether the delivery is complete with this result.
     **/
    public void deliver(T msg);

    /** deliver an error result for the computation
     * @param cause throwable that describes the error
     */
    void deliverError(Throwable cause);

    /** whether or not the deliverable has been completed before.
     */
    boolean isDone();

    boolean cancel(boolean mayInterruptIfRunning);
    
    OFMessage getRequest();
}
