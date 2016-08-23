package net.floodlightcontroller.core;

import org.projectfloodlight.openflow.protocol.OFMessage;

import com.google.common.util.concurrent.AbstractFuture;

/** Implementation of a ListenableFuture that provides a Deliverable interface to
 *  the provider.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 * @see Deliverable
 * @param <T>
 */
public class DeliverableListenableFuture<T> extends AbstractFuture<T> implements Deliverable<T> {

	private OFMessage request;

	public DeliverableListenableFuture(OFMessage msg) {
		this.request = msg;
	}

	@Override
    public void deliver(final T result) {
        set(result);
    }

    @Override
    public void deliverError(final Throwable cause) {
        setException(cause);
    }

    @Override
    public OFMessage getRequest() {
    	return request;
    }
}
