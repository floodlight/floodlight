package org.sdnplatform.sync;

import java.util.Iterator;

/**
 * A listener interface that will receive updates on a particular store
 * @author readams
 * @param <K> the key type for the store
 */
public interface IStoreListener<K> {
    /**
     * The origin of the update
     * @author readams
     */
    public enum UpdateType {
        /**
         * An update that originated from a write to the local store
         */
        LOCAL,
        /**
         * An update that originated from a value synchronized from a remote
         * node.  Note that it is still possible that this includes only
         * information that originated from the current node.
         */
        REMOTE
    };

    /**
     * Called when keys in the store are modified or deleted.
     * @param type the type of the update
     * @see UpdateType
     */
    public void keysModified(Iterator<K> keys, UpdateType type);
}
