package org.sdnplatform.sync.error;

/**
 * Generic exception type for sync service exceptions
 * @author readams
 */
public class SyncException extends Exception {

    private static final long serialVersionUID = -6150348258087759055L;

    public enum ErrorType {
        SUCCESS(0),
        GENERIC(1),
        INCONSISTENT_DATA(2),
        OBSOLETE_VERSION(3),
        UNKNOWN_STORE(4),
        SERIALIZATION(5),
        PERSIST(6),
        HANDSHAKE_TIMEOUT(7),
        REMOTE_STORE(8),
        AUTH(9);
        
        private final int value;
        
        ErrorType(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }
    
    public SyncException() {
        super();
    }

    public SyncException(String message, Throwable cause) {
        super(message, cause);
    }

    public SyncException(String message) {
        super(message);
    }

    public SyncException(Throwable cause) {
        super(cause);
    }
    
    public ErrorType getErrorType() {
        return ErrorType.GENERIC;
    }
    
    public static SyncException newInstance(ErrorType type,
                                            String message, Throwable cause) {
        switch (type) {
            case INCONSISTENT_DATA:
                return new InconsistentDataException(message, null);
            case OBSOLETE_VERSION:
                return new ObsoleteVersionException(message, cause);
            case UNKNOWN_STORE:
                return new UnknownStoreException(message, cause);
            case SERIALIZATION:
                return new SerializationException(message, cause);
            case PERSIST:
                return new PersistException(message, cause);
            case HANDSHAKE_TIMEOUT:
                return new HandshakeTimeoutException();
            case REMOTE_STORE:
                return new RemoteStoreException(message, cause);
            case AUTH:
                return new AuthException(message, cause);
            case GENERIC:
            default:
                return new SyncException(message, cause);
        }
    }
}
