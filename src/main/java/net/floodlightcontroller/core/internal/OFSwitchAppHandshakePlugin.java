package net.floodlightcontroller.core.internal;

import java.util.concurrent.TimeUnit;

import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.OFSwitchHandshakeHandler.WaitAppHandshakeState;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

/**
 * This class is a plugin that can be used by applications to tap into the
 * switch handshake. It operates much like the switch handshake. Messages should
 * be sent upon entering the plugin and OF response messages should be handled
 * just like any part of the switch handshake.
 *
 * @author Jason Parraga <jason.parraga@bigswitch.com>
 */
public abstract class OFSwitchAppHandshakePlugin {

    private static final Logger log = LoggerFactory.getLogger(OFSwitchAppHandshakePlugin.class);

    private WaitAppHandshakeState state;
    @edu.umd.cs.findbugs.annotations.SuppressWarnings("URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    private IOFSwitch sw;
    private volatile Timeout timeout;

    private final PluginResult defaultResult;
    private final int timeoutS;

    /**
     * Constructor for OFSwitchAppHandshakePlugin
     * @param defaultResult the default result in the event of a timeout
     * @param defaultTimeoutS the timeout length in seconds
     */
    protected OFSwitchAppHandshakePlugin(PluginResult defaultResult, int timeoutS){
        Preconditions.checkNotNull(defaultResult, "defaultResult");
        Preconditions.checkNotNull(timeoutS, "timeoutS");

        this.defaultResult = defaultResult;
        this.timeoutS = timeoutS;
    }

    /**
     * Process incoming OF Messages
     *
     * @param m The OF Message received
     */
    protected abstract void processOFMessage(OFMessage m);

    /**
     * Enter this plugin. Should be used to send messages.
     */
    protected abstract void enterPlugin();

    /**
     * Gets the switch associated with the handshake for use by the plugin
     * writer.
     *
     * @return the switch associated with the handshake.
     */
    protected IOFSwitch getSwitch() {
        return this.sw;
    }

    /**
     * Initialization for plugin called by the OFSwitchHandshakeHandler
     *
     * @param state the current state of the OFSwitchHandshakeHandler
     * @param sw the current switch of the OFSwitchHandshakeHandler
     */
    final void init(WaitAppHandshakeState state, IOFSwitch sw, Timer timer) {
        this.state = state;
        this.sw = sw;
        this.timeout = timer.newTimeout(new PluginTimeoutTask(), timeoutS, TimeUnit.SECONDS);
    }

    /**
     * Called to denote that the plugin has finished
     *
     * @param result
     *            the result of the plugin in regards to handshaking
     */
    protected final void exitPlugin(PluginResult result) {
        timeout.cancel();
        state.exitPlugin(result);
    }

    /**
     * Plugin timeout task that will exit the plugin
     * with the default result value when timed out.
     *
     */
    private final class PluginTimeoutTask implements TimerTask {

        @Override
        public void run(Timeout timeout) throws Exception {
            if (!timeout.isCancelled()) {
                log.warn("App handshake plugin for {} timed out. Returning result {}.",
                         sw, defaultResult);
                exitPlugin(defaultResult);
            }
        }
    }

    public enum PluginResultType {
        CONTINUE(),
        DISCONNECT(),
        QUARANTINE();
    }
}
