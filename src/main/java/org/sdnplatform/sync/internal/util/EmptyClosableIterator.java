package org.sdnplatform.sync.internal.util;

import java.util.NoSuchElementException;

import org.sdnplatform.sync.IClosableIterator;


public class EmptyClosableIterator<T> implements IClosableIterator<T> {
    
    public boolean hasNext() {
        return false;
    }

    public T next() {
        throw new NoSuchElementException();
    }

    public void remove() {
        throw new NoSuchElementException();
    }

    @Override
    public void close() {
        // no-op
    }
}
