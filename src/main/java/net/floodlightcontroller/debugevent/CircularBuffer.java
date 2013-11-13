package net.floodlightcontroller.debugevent;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CircularBuffer<T> implements Iterable<T>{
    protected static Logger log = LoggerFactory.getLogger(CircularBuffer.class);
    private final LinkedBlockingDeque<T> buffer;

    public CircularBuffer(int capacity) {
        this.buffer = new LinkedBlockingDeque<T>(capacity);
    }

    /**
     * Adding an element to the circular buffer implies adding the element to
     * the tail of the deque. In doing so, if the capacity of the buffer has
     * been exhausted, then the deque's head should be removed to preserve the
     * circular nature of the buffer. A LinkedBlockingDeque is used because of its
     * concurrent nature and the fact that adding to tail and removing from head are
     * both O(1) operations. The removed head is returned to the caller for reuse.
     *
     * @param e    the element to be added
     * @return     removed element (for reuse) or null
     */
    public T add(T e) {
        T oldE = null;
        while (!buffer.offerLast(e)) {
            oldE = buffer.poll();
        }
        return oldE;
    }

    /**
     * The basic idea here is that an ArrayList has been passed in, which may or may not
     * have a size bigger that the actual number of elements that are meant to
     * be flushed to the Circular Buffer. Thus the 'uptoIndex' parameter specifies
     * the number of elements that need to be flushed starting from index 0.
     * Note that after flushing, the circular buffer may return a memory unit (of type T)
     * for reuse in the list, if the circular buffer popped off memory to preserve
     * its circular nature. Or it may just return null if nothing was popped off.
     * Either way, the list that is returned by this method, is of the SAME SIZE
     * as the list passed in, as ArrayLists can hold null elements. The only difference
     * is that the list returned will have elements that reference old popped-off memory
     * from the circular-buffer or null.
     *
     * @param elist         the ArrayList to flush into the circular buffer.
     * @param uptoIndex     flush starting from index 0 upto but not including
     *                      index 'uptoIndex'.
     * @return              the 'elist' passed in with members now pointing to
     *                      to null or old-memory for reuse. The returned list
     *                      if of the same size as 'elist'.
     */
    public ArrayList<T> addAll(ArrayList<T> elist, int uptoIndex) {
        if (uptoIndex > elist.size()) {
            log.error("uptoIndex is greater than elist size .. aborting addAll");
            return elist;
        }
        for (int index=0; index < uptoIndex; index++) {
            T e = elist.get(index);
            if (e != null) {
                elist.set(index, add(e));
            }
        }
        return elist;
    }

    /**
     * Returns an iterator over the elements in the circular buffer in proper sequence.
     * The elements will be returned in order from most-recent to oldest.
     * The returned Iterator is a "weakly consistent" iterator that will never
     * throw ConcurrentModificationException, and guarantees to traverse elements
     * as they existed upon construction of the iterator, and may (but is not
     * guaranteed to) reflect any modifications subsequent to construction.
     */
    @Override
    public Iterator<T> iterator() {
        return buffer.descendingIterator();
    }

    public int size() {
        return buffer.size();
    }

    /**
     *  Atomically removes all elements in the circular buffer
     */
    public void clear() {
        buffer.clear();
    }
}
