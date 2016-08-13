package net.floodlightcontroller.core.util;

public class AppIDNotRegisteredException extends AppIDException {
    private static final long serialVersionUID = -9195312786292237763L;

    public AppIDNotRegisteredException(long application) {
        super("Application Id " + application + " has not been registered");
    }

}
