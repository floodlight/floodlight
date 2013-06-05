package org.sdnplatform.sync.error;

/**
 * An error occurred while serializing or deserializing objects
 * @author readams
 */
public class SerializationException extends SyncException {

    private static final long serialVersionUID = 6633759330354187L;

    public SerializationException() {
        super();
    }

    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public SerializationException(String message) {
        super(message);
    }

    public SerializationException(Throwable cause) {
        super(cause);
    }
    
    @Override
    public ErrorType getErrorType() {
        return ErrorType.SERIALIZATION;
    }
}
