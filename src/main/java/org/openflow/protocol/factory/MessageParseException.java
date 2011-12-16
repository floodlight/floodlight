package org.openflow.protocol.factory;

/**
 * Exception thrown when an openflow message fails to parse properly
 */
public class MessageParseException extends Exception {
    /**
     * 
     */
    private static final long serialVersionUID = -75893812926304726L;

    public MessageParseException() {
        super();
    }

    public MessageParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public MessageParseException(String message) {
        super(message);
    }

    public MessageParseException(Throwable cause) {
        super(cause);
    }
}
