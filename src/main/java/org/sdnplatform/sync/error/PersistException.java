package org.sdnplatform.sync.error;

/**
 * An error with a persistence layer
 * @author readams
 *
 */
public class PersistException extends SyncException {
    private static final long serialVersionUID = -1374782534201553648L;

    public PersistException() {
        super();
    }

    public PersistException(String message, Throwable cause) {
        super(message, cause);
    }

    public PersistException(String message) {
        super(message);
    }

    public PersistException(Throwable cause) {
        super(cause);
    }
    
    @Override
    public ErrorType getErrorType() {
        return ErrorType.PERSIST;
    }
}
