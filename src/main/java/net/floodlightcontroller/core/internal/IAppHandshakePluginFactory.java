package net.floodlightcontroller.core.internal;

/**
 * This interface creates a contract used by the switch handshake handler. Each
 * switch that is connected needs it's own running instance of the registered
 * plugins. Thus is depends on a factory to churn out these instances.
 * @author Jason Parraga <Jason.Parraga@bigswitch.com>
 *
 */
public interface IAppHandshakePluginFactory {

    /**
     * Create an instance of OFSwitchAppHandshakePlugin
     * @return an instance of OFSwitchAppHandshakePlugin
     */
    OFSwitchAppHandshakePlugin createPlugin();
}


