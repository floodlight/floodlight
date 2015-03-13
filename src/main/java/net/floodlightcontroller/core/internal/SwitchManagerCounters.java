package net.floodlightcontroller.core.internal;

import net.floodlightcontroller.debugcounter.IDebugCounter;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.IDebugCounterService.MetaData;

public class SwitchManagerCounters {

    public final String prefix = OFSwitchManager.class.getSimpleName();
    public final IDebugCounter invalidPortsChanged;
    public final IDebugCounter switchConnected;
    public final IDebugCounter invalidSwitchActivatedWhileSlave;
    public final IDebugCounter switchActivated;
    public final IDebugCounter switchDeactivated;
    public final IDebugCounter errorActivatedSwitchNotPresent;
    public final IDebugCounter switchWithSameDpidActivated;
    public final IDebugCounter switchDisconnected;
    public final IDebugCounter switchPortChanged;
    public final IDebugCounter switchOtherChange;
    public final IDebugCounter switchDisconnectReadTimeout;
    public final IDebugCounter switchDisconnectHandshakeTimeout;
    public final IDebugCounter switchDisconnectIOError;
    public final IDebugCounter switchDisconnectParseError;
    public final IDebugCounter switchDisconnectSwitchStateException;
    public final IDebugCounter rejectedExecutionException;
    public final IDebugCounter switchDisconnectOtherException;
    public final IDebugCounter unhandledMessage;
    public final IDebugCounter packetInWhileSwitchIsSlave;
    public final IDebugCounter roleNotResentBecauseRolePending;
    public final IDebugCounter epermErrorWhileSwitchIsMaster;
    public final IDebugCounter roleReplyTimeout;
    public final IDebugCounter roleReplyReceived;
    public final IDebugCounter roleReplyErrorUnsupported;
    public final IDebugCounter switchSslConfigurationError;

    public SwitchManagerCounters(IDebugCounterService debugCounters) {
        debugCounters.registerModule(prefix);
        invalidPortsChanged =
                debugCounters.registerCounter(
                            prefix, "invalid-ports-changed",
                            "Received an unexpected ports changed " +
                            "notification while the controller was in " +
                            "SLAVE role.",
                            MetaData.WARN);

            invalidSwitchActivatedWhileSlave =
                debugCounters.registerCounter(
                            prefix, "invalid-switch-activated-while-slave",
                            "Received an unexpected switchActivated " +
                            "notification while the controller was in " +
                            "SLAVE role.",
                            MetaData.WARN);

            switchActivated =
                debugCounters.registerCounter(
                            prefix, "switch-activated",
                            "A switch connected to this controller is now " +
                            "in MASTER role");

            switchDeactivated =
                    debugCounters.registerCounter(
                                prefix, "switch-activated",
                                "A switch connected to this controller is now " +
                                "in SLAVE role");

            errorActivatedSwitchNotPresent = // err
                debugCounters.registerCounter(
                            prefix, "error-same-switch-reactivated",
                            "A switch that was already in active state " +
                            "was activated again. This indicates a " +
                            "controller defect",
                            MetaData.ERROR);

            switchWithSameDpidActivated = // warn
                debugCounters.registerCounter(
                            prefix, "switch-with-same-dpid-activated",
                            "A switch with the same DPID as another switch " +
                            "connected to the controller. This can be " +
                            "caused by multiple switches configured with " +
                            "the same DPID or by a switch reconnecting very " +
                            "quickly.",
                            MetaData.WARN);

            switchDisconnected =
                debugCounters.registerCounter(
                            prefix, "switch-disconnected",
                            "FIXME: switch has disconnected");

            switchPortChanged =
                debugCounters.registerCounter(
                            prefix, "switch-port-changed",
                            "Number of times switch ports have changed");
            switchOtherChange =
                debugCounters.registerCounter(
                            prefix, "switch-other-change",
                            "Number of times other information of a switch " +
                            "has changed.");

            switchDisconnectReadTimeout =
                    debugCounters.registerCounter(
                                prefix, "switch-disconnect-read-timeout",
                                "Number of times a switch was disconnected due " +
                                "due the switch failing to send OpenFlow " +
                                "messages or responding to OpenFlow ECHOs",
                                MetaData.ERROR);
                switchDisconnectHandshakeTimeout =
                    debugCounters.registerCounter(
                                prefix, "switch-disconnect-handshake-timeout",
                                "Number of times a switch was disconnected " +
                                "because it failed to complete the handshake " +
                                "in time.",
                                MetaData.ERROR);
                switchDisconnectIOError =
                    debugCounters.registerCounter(
                                prefix, "switch-disconnect-io-error",
                                "Number of times a switch was disconnected " +
                                "due to IO errors on the switch connection.",
                                MetaData.ERROR);
                switchDisconnectParseError =
                    debugCounters.registerCounter(
                                prefix, "switch-disconnect-parse-error",
                               "Number of times a switch was disconnected " +
                               "because it sent an invalid packet that could " +
                               "not be parsed",
                               MetaData.ERROR);

                switchDisconnectSwitchStateException =
                    debugCounters.registerCounter(
                                prefix, "switch-disconnect-switch-state-exception",
                                "Number of times a switch was disconnected " +
                                "because it sent messages that were invalid " +
                                "given the switch connection's state.",
                                MetaData.ERROR);
                rejectedExecutionException =
                    debugCounters.registerCounter(
                                prefix, "rejected-execution-exception",
                                "TODO",
                                MetaData.ERROR);

                switchDisconnectOtherException =
                    debugCounters.registerCounter(
                                prefix,  "switch-disconnect-other-exception",
                                "Number of times a switch was disconnected " +
                                "due to an exceptional situation not covered " +
                                "by other counters",
                                MetaData.ERROR);

                switchConnected =
                    debugCounters.registerCounter(
                                prefix, "switch-connected",
                                "Number of times a new switch connection was " +
                                "established");

                unhandledMessage =
                    debugCounters.registerCounter(
                                prefix, "unhandled-message",
                                "Number of times an OpenFlow message was " +
                                "received that the controller ignored because " +
                                "it was inapproriate given the switch " +
                                "connection's state.",
                                MetaData.WARN);

                packetInWhileSwitchIsSlave =
                        debugCounters.registerCounter(
                                    prefix, "packet-in-while-switch-is-slave",
                                    "Number of times a packet in was received " +
                                    "from a switch that was in SLAVE role. " +
                                    "Possibly inidicates inconsistent roles.");
                    epermErrorWhileSwitchIsMaster =
                        debugCounters.registerCounter(
                                    prefix, "eperm-error-while-switch-is-master",
                                    "Number of times a permission error was " +
                                    "received while the switch was in MASTER role. " +
                                    "Possibly inidicates inconsistent roles.",
                                    MetaData.WARN);

                    roleNotResentBecauseRolePending =
                        debugCounters.registerCounter(
                                    prefix, "role-not-resent-because-role-pending",
                                    "The controller tried to reestablish a role " +
                                    "with a switch but did not do so because a " +
                                    "previous role request was still pending");
                    roleReplyTimeout =
                        debugCounters.registerCounter(
                                    prefix, "role-reply-timeout",
                                    "Number of times a role request message did not " +
                                    "receive the expected reply from a switch",
                                    MetaData.WARN);

                    roleReplyReceived = // expected RoleReply received
                        debugCounters.registerCounter(
                                    prefix, "role-reply-received",
                                    "Number of times the controller received the " +
                                    "expected role reply message from a switch");

                    roleReplyErrorUnsupported =
                        debugCounters.registerCounter(
                                    prefix, "role-reply-error-unsupported",
                                    "Number of times the controller received an " +
                                    "error from a switch in response to a role " +
                                    "request indicating that the switch does not " +
                                    "support roles.");
                    
                    switchSslConfigurationError =
                            debugCounters.registerCounter(
                                        prefix, "switch-ssl-configuration-error",
                                        "Number of times the controller could not " +
                                        "handshake with a switch due to an " +
                                        "IllegalArgumentException, which is likely " +
                                        "due to the switch trying to speak SSL whereas " +
                                        "the controller wants to use vanilla TCP.");
    }

    public String getPrefix(){
        return this.prefix;
    }

}
