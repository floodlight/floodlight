package org.sdnplatform.sync.error;

/**
 * A runtime exception that wraps a SyncException.  This is thrown from
 * standard interfaces that don't support an appropriate exceptional type.
 * @author readams
 */
public class SyncRuntimeException extends RuntimeException {

    private static final long serialVersionUID = -5357245946596447913L;

    public SyncRuntimeException(String message, SyncException cause) {
        super(message, cause);
    }

    public SyncRuntimeException(SyncException cause) {
        super(cause);
    }
}
