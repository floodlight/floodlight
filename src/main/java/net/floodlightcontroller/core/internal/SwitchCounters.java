package net.floodlightcontroller.core.internal;

import org.projectfloodlight.openflow.types.DatapathId;

import net.floodlightcontroller.debugcounter.IDebugCounter;
import net.floodlightcontroller.debugcounter.IDebugCounterService;

public class SwitchCounters {

    public final String moduleName = OFSwitchManager.class.getSimpleName();
    private IDebugCounterService debugCounterService;
    private String dpidString;
    public final IDebugCounter deviceAdded;
    public final IDebugCounter deviceRemoved;
    public final IDebugCounter portEnabled;
    public final IDebugCounter portDisabled;
    public final IDebugCounter portUp;
    public final IDebugCounter portDown;
    public final IDebugCounter packetIn;
    public final IDebugCounter packetOut;
    public final IDebugCounter flowAdded;
    public final IDebugCounter flowRemoved;

    /**
     * Maintains counters for a connected switch. The OFSwitchManager should be
     * registered with the IDebugCounterService prior to adding a particular
     * switch's counters. All switch counters will be housed under the umbrella
     * of the OFSwitchManagerModule, where each switch's counters can be accessed
     * by the DPID of that switch followed by the hierarchy of the counter:
     *    ofswitchmanagerservice/00:11:22:33:44:55:66:77/device-added
     *    ofswitchmanagerservice/88:99:AA:BB:CC:DD:EE:FF/port-down
     *    
     * @param debugCounters
     * @param dpid
     */
    public SwitchCounters(IDebugCounterService debugCounters, DatapathId dpid) {
    	debugCounterService = debugCounters;
    	dpidString = dpid.toString();
    	
    	/* Register the switch itself, although it's not a counter technically.
    	 * An exception will be thrown if a sub-counter of the DPID is registered
    	 * prior to the DPID itself.
    	 */
    	debugCounters.registerCounter(moduleName, dpidString, "Switch DPID base for counters");
    	
        deviceAdded =
                debugCounters.registerCounter(
                            moduleName, dpidString + "/device-added",
                            "Device added to switch");

        deviceRemoved =
                debugCounters.registerCounter(
                            moduleName, dpidString + "/device-removed",
                            "Device removed from switch");
        
            portEnabled =
                debugCounters.registerCounter(
                            moduleName, dpidString + "/port-enabled",
                            "Port enabled on switch");

            portDisabled =
                debugCounters.registerCounter(
                            moduleName, dpidString + "/port-disabled",
                            "Port disabled on switch");

            portUp =
                    debugCounters.registerCounter(
                                moduleName, dpidString + "/port-up",
                                "Port up on switch; device connected");

            portDown = 
                debugCounters.registerCounter(
                            moduleName, dpidString + "/port-down",
                            "Port down on switch; device disconnected");

            packetIn =
                debugCounters.registerCounter(
                            moduleName, dpidString + "/packet-in",
                            "Packet in from switch");

            packetOut =
                debugCounters.registerCounter(
                            moduleName, dpidString + "/packet-out",
                            "Packet out to switch");

            flowAdded =
                debugCounters.registerCounter(
                            moduleName, dpidString + "/flow-added",
                            "Flow added to switch");
            flowRemoved =
                debugCounters.registerCounter(
                            moduleName, dpidString + "/flow-removed",
                            "Flow removed from switch");
    }

    public String getPrefix(){
        return this.moduleName;
    }
    
    /**
     * Remove all counters from the IDebugCounterService. Should be done
     * if the switch disconnects from the controller, in which case all
     * the counters will be invalid.
     * @return true if successful; false if switch was not found
     */
    public boolean uninstallCounters() {
    	return debugCounterService.removeCounterHierarchy(moduleName, dpidString);
    }
}
