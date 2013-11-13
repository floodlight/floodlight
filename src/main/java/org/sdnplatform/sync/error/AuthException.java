package org.sdnplatform.sync.error;

public class AuthException extends SyncException {
    private static final long serialVersionUID = -2274293152220154863L;

    public AuthException() {
        super();
    }

    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }

    public AuthException(String message) {
        super(message);
    }

    public AuthException(Throwable cause) {
        super(cause);
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.AUTH;
    }
}
