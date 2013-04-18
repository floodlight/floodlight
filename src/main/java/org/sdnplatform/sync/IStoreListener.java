package org.sdnplatform.sync;

import java.util.Iterator;

public interface IStoreListener<K> {
    /**
     * Called when keys in the store are modified or deleted.
     */
    public void keysModified(Iterator<K> keys);
}
