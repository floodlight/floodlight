package net.floodlightcontroller.core.util;

public class AppIDInUseException extends Exception {
    private static final long serialVersionUID = 3167241821651094997L;

    public AppIDInUseException(int appId, String appName) {
        super("Application ID " + appId + " already used by " + appName);
    }
}