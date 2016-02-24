package net.floodlightcontroller.core.internal;

import net.floodlightcontroller.debugcounter.IDebugCounter;
import net.floodlightcontroller.debugcounter.IDebugCounterService;
import net.floodlightcontroller.debugcounter.IDebugCounterService.MetaData;

 public class RoleManagerCounters {

        public final String prefix = RoleManager.class.getSimpleName();
        public final IDebugCounter setSameRole;
        public final IDebugCounter setRoleMaster;

        public RoleManagerCounters(IDebugCounterService debugCounters) {
            debugCounters.registerModule(prefix);
            setSameRole =
                    debugCounters.registerCounter(
                                prefix, "set-same-role",
                                "Controller received a role request for the same " +
                                "role the controller already had",
                                MetaData.WARN);

                setRoleMaster =
                    debugCounters.registerCounter(
                                prefix, "set-role-master",
                                "Controller received a role request with role of " +
                                "MASTER. This counter can be at most 1.");
        }

        public String getPrefix(){
            return this.prefix;
        }
    }