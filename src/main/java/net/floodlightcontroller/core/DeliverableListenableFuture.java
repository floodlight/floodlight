package net.floodlightcontroller.core;

import java.util.concurrent.CompletableFuture;

/** Implementation of a ListenableFuture that provides a Deliverable interface to
 *  the provider.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 * @see Deliverable
 * @param <T>
 */
public class DeliverableListenableFuture<T> extends CompletableFuture<T> implements Deliverable<T> {
    @Override
    public void deliver(final T result) {
        complete(result);
    }

    @Override
    public void deliverError(final Throwable cause) {
    	completeExceptionally(cause);
    }
}
