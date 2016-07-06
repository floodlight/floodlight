package net.floodlightcontroller.core.internal;

import java.util.List;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchDriver;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.LogicalOFMessageCategory;
import net.floodlightcontroller.core.module.IFloodlightService;

import org.projectfloodlight.openflow.types.DatapathId;

public interface IOFSwitchService extends IFloodlightService {

    /**
     * Get's the switch map stored in the switch manager
     * @return the map of switches known by the switch manager
     */
    Map<DatapathId, IOFSwitch> getAllSwitchMap();

    /**
     * If the switch with the given DPID is known to any controller in the
     * cluster, this method returns the associated IOFSwitch instance. As such
     * the returned switches not necessarily connected or in master role for
     * the local controller.
     *
     * Multiple calls to this method with the same DPID may return different
     * IOFSwitch references. A caller must not store or otherwise rely on
     * IOFSwitch references to be constant over the lifecycle of a switch.
     *
     * @param dpid the dpid of the switch to query
     * @return the IOFSwitch instance associated with the dpid, null if no
     * switch with the dpid is known to the cluster
     */
    IOFSwitch getSwitch(DatapathId dpid);

    /**
     * If the switch with the given DPID is known to any controller in the
     * cluster, this method returns the associated IOFSwitch instance. As such
     * the returned switches not necessarily connected or in master role for
     * the local controller.
     *
     * Multiple calls to this method with the same DPID may return different
     * IOFSwitch references. A caller must not store or otherwise rely on
     * IOFSwitch references to be constant over the lifecycle of a switch.
     *
     * @param dpid the dpid of the switch to query
     * @return the IOFSwitch instance associated with the dpid, null if no
     * switch with the dpid is known to the cluster OR if the switch status
     * is not considered visible.
     */
    IOFSwitch getActiveSwitch(DatapathId dpid);

    /**
     * Add a switch listener
     * @param listener The module that wants to listen for events
     */
    void addOFSwitchListener(IOFSwitchListener listener);
    
    /**
     * Add a switch driver
     * @param manufacturerDescriptionPrefix
     * @param driver
     */
    void addOFSwitchDriver(String manufacturerDescriptionPrefix, IOFSwitchDriver driver);

    /**
     * Remove a switch listener
     * @param listener The The module that no longer wants to listen for events
     */
    void removeOFSwitchListener(IOFSwitchListener listener);

    /**
     * Registers a logical OFMessage category to be used by an application
     * @param category the logical OFMessage category
     */
    void registerLogicalOFMessageCategory(LogicalOFMessageCategory category);

    /**
     * Registers an app handshake plugin to be used during switch handshaking.
     * @param plugin the app handshake plugin to be used during switch handshaking.
     */
    void registerHandshakePlugin(IAppHandshakePluginFactory plugin);

    /**
     * Returns a snapshot of the set DPIDs for all known switches.
     *
     * The returned set is owned by the caller: the caller can modify it at
     * will and changes to the known switches are not reflected in the returned
     * set. The caller needs to call getAllSwitchDpids() if an updated
     * version is needed.
     *
     * See {@link #getSwitch(long)} for what  "known" switch is.
     * @return the set of DPIDs of all known switches
     */
    Set<DatapathId> getAllSwitchDpids();

    /**
     * Gets an immutable list of handshake handlers.
     * @return an immutable list of handshake handlers.
     */
    List<OFSwitchHandshakeHandler> getSwitchHandshakeHandlers();

}
