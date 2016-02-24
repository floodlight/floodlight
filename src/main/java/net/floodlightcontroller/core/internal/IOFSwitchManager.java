package net.floodlightcontroller.core.internal;

import java.util.List;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IOFConnectionBackend;
import net.floodlightcontroller.core.IOFSwitch.SwitchStatus;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchBackend;
import net.floodlightcontroller.core.IOFSwitchDriver;
import net.floodlightcontroller.core.LogicalOFMessageCategory;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.SwitchDescription;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.types.DatapathId;

import com.google.common.collect.ImmutableList;

public interface IOFSwitchManager {

    /**
     * Called when a switch is added.
     * @param sw the added switch
     */
    void switchAdded(IOFSwitchBackend sw);

    /**
     * Called when a switch disconnects
     * @param sw the removed switch
     */
    void switchDisconnected(IOFSwitchBackend sw);

    /**
     * Indicates that ports on the given switch have changed. Enqueue a
     * switch update.
     * @param sw the added switch
     */
    void notifyPortChanged(IOFSwitchBackend sw, OFPortDesc port,
                           PortChangeType type);

    /**
     * Relays to ISwitchDriverRegistry
     */
    IOFSwitchBackend getOFSwitchInstance(IOFConnectionBackend connection,
                                         SwitchDescription description,
                                         OFFactory factory,
                                         DatapathId datapathId);

    /**
     * Relays an upstream message to the controller to dispatch to listeners.
     * @param sw The switch the message was received on.
     * @param m The message received.
     * @param bContext the Floodlight context of the message, normally null in this case.
     */
    void handleMessage(IOFSwitchBackend sw, OFMessage m, FloodlightContext bContext);
    
    /**
     * Process written messages through the message listeners for the controller
     * @param sw The switch being written to
     * @param m the message
     */
    public void handleOutgoingMessage(IOFSwitch sw, OFMessage m);

    /**
     * Gets an unmodifiable collection of OFSwitchHandshakeHandlers
     * @return an unmodifiable collection of OFSwitchHandshakeHandlers
     */
    ImmutableList<OFSwitchHandshakeHandler> getSwitchHandshakeHandlers();

    /**
     * Adds an OFSwitch driver
     *  @param manufacturerDescriptionPrefix Register the given prefix
     * with the driver.
     * @param driver A IOFSwitchDriver instance to handle IOFSwitch instantiation
     * for the given manufacturer description prefix
     * @throws IllegalStateException If the the manufacturer description is
     * already registered
     * @throws NullPointerExeption if manufacturerDescriptionPrefix is null
     * @throws NullPointerExeption if driver is null
     */
    void addOFSwitchDriver(String manufacturerDescriptionPrefix,
                           IOFSwitchDriver driver);

    /**
     * Handles all changes to the switch status. Will alert listeners and manage
     * the internal switch map appropriately.
     * @param sw the switch that has changed
     * @param oldStatus the switch's old status
     * @param newStatus the switch's new status
     */
    void switchStatusChanged(IOFSwitchBackend sw, SwitchStatus oldStatus,
            SwitchStatus newStatus);

    /**
     * Gets the number of connections required by the application
     * @return the number of connections required by the applications
     */
    int getNumRequiredConnections();

    /**
     * Record a switch event in in-memory debug-event
     * @param switchDpid
     * @param reason Reason for this event
     * @param flushNow see debug-event flushing in IDebugEventService
     */
    public void addSwitchEvent(DatapathId switchDpid, String reason, boolean flushNow);

    /**
     * Get the list of handshake plugins necessary for the switch handshake.
     * @return the list of handshake plugins registered by applications.
     */
    List<IAppHandshakePluginFactory> getHandshakePlugins();

    /**
     * Get the switch manager's counters
     * @return the switch manager's counters
     */
    SwitchManagerCounters getCounters();

    /**
     * Checks to see if the supplied category has been registered with the controller
     * @param category the logical OF Message category to check or
     * @return true if registered
     */
    boolean isCategoryRegistered(LogicalOFMessageCategory category);

    void handshakeDisconnected(DatapathId dpid);

}
