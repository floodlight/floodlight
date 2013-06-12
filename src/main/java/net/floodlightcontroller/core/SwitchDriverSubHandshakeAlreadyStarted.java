package net.floodlightcontroller.core;

/**
 * Thrown when IOFSwitch.startDriverHandshake() is called more than once.
 * @author gregor
 *
 */
public class SwitchDriverSubHandshakeAlreadyStarted extends
    SwitchDriverSubHandshakeException {
    private static final long serialVersionUID = -5491845708752443501L;

    public SwitchDriverSubHandshakeAlreadyStarted() {
        super();
    }
}
