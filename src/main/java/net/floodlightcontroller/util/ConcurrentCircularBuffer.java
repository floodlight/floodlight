package net.floodlightcontroller.util;


import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Array;



public class ConcurrentCircularBuffer <T> {
    private final AtomicInteger cursor = new AtomicInteger();
    private final Object[]      buffer;
    private final Class<T>      type;

    public ConcurrentCircularBuffer (final Class <T> type, 
                                     final int bufferSize) 
	{
	    if (bufferSize < 1) {
		throw new IllegalArgumentException(
                "Buffer size must be a positive value"
						   );
	    }

	    this.type   = type;
	    this.buffer = new Object [ bufferSize ];
	}

    public void add (T sample) {
        buffer[ cursor.getAndIncrement() % buffer.length ] = sample;
    }

    @SuppressWarnings("unchecked")
	public T[] snapshot () {
        Object[] snapshots = new Object [ buffer.length ];
            
        /* Identify the start-position of the buffer. */
        int before = cursor.get();

        /* Terminate early for an empty buffer. */
        if (before == 0) {
            return (T[]) Array.newInstance(type, 0);
        }

        System.arraycopy(buffer, 0, snapshots, 0, buffer.length);

        int after          = cursor.get();
        int size           = buffer.length - (after - before);
        int snapshotCursor = before - 1;

        /* The entire buffer was replaced during the copy. */
        if (size <= 0) {
            return (T[]) Array.newInstance(type, 0);
        }

        int start = snapshotCursor - (size - 1);
        int end   = snapshotCursor;

        if (snapshotCursor < snapshots.length) {
            size   = snapshotCursor + 1;
            start  = 0;
        }

        /* Copy the sample snapshot to a new array the size of our stable
         * snapshot area.
         */
        T[] result = (T[]) Array.newInstance(type, size);

        int startOfCopy = start % snapshots.length;
        int endOfCopy   = end   % snapshots.length;

        /* If the buffer space wraps the physical end of the array, use two
         * copies to construct the new array.
         */
        if (startOfCopy > endOfCopy) {
            System.arraycopy(snapshots, startOfCopy,
                             result, 0, 
                             snapshots.length - startOfCopy);
            System.arraycopy(snapshots, 0,
                             result, (snapshots.length - startOfCopy),
                             endOfCopy + 1);
        }
        else {
            /* Otherwise it's a single continuous segment, copy the whole thing
             * into the result.
             */
            System.arraycopy(snapshots, startOfCopy, result, 0, size);
        }

        return (T[]) result;
    }
}