package net.floodlightcontroller.core.util;

public class AppIDInUseException extends AppIDException {
    private static final long serialVersionUID = 3167241821651094997L;

    public AppIDInUseException(int appId, String oldAppName,
                               String newAppName) {
        super(String.format("Tried to register application IdD %s for %s, but" +
                "already registered for %s.", appId, oldAppName, newAppName));
    }
}