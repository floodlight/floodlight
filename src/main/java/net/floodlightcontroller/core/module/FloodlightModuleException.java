package net.floodlightcontroller.core.module;

public class FloodlightModuleException extends Exception {
	private static final long serialVersionUID = 1L;

	public FloodlightModuleException(String error) {
		super(error);
	}

    public FloodlightModuleException() {
        super();
    }

    public FloodlightModuleException(String message, Throwable cause) {
        super(message, cause);
    }

    public FloodlightModuleException(Throwable cause) {
        super(cause);
    }
}
