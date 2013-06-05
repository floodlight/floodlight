package org.sdnplatform.sync.internal;

import java.util.List;
import java.util.Map.Entry;

import org.sdnplatform.sync.IClosableIterator;
import org.sdnplatform.sync.Versioned;
import org.sdnplatform.sync.internal.util.ByteArray;


public class Cursor implements
    IClosableIterator<Entry<ByteArray, List<Versioned<byte[]>>>> {
    private final int cursorId;
    private final 
        IClosableIterator<Entry<ByteArray, 
                                List<Versioned<byte[]>>>> delegate;
    
    public Cursor(int cursorId,
                  IClosableIterator<Entry<ByteArray, 
                                          List<Versioned<byte[]>>>> delegate) {
        super();
        this.cursorId = cursorId;
        this.delegate = delegate;
    }

    @Override
    public boolean hasNext() {
        return delegate.hasNext();
    }

    @Override
    public Entry<ByteArray, List<Versioned<byte[]>>> next() {
        return delegate.next();
    }

    @Override
    public void remove() {
        delegate.remove();
    }

    @Override
    public void close() {
        delegate.close();
    }
    
    public int getCursorId() {
        return this.cursorId;
    }        
}