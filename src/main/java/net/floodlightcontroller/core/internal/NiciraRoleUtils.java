package net.floodlightcontroller.core.internal;

import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFNiciraControllerRole;
import org.projectfloodlight.openflow.protocol.OFNiciraControllerRoleReply;

/** static utilities to convert between Pre-OF1.2 "Nicira Style" roles and OF1.2+ OpenFlow
 *  standard roles.
 *  @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class NiciraRoleUtils {
    private NiciraRoleUtils() {}

    public static OFControllerRole niciraToOFRole(OFNiciraControllerRoleReply roleReply) {
        switch(roleReply.getRole()) {
            case ROLE_MASTER:
                return OFControllerRole.ROLE_MASTER;
            case ROLE_OTHER:
                return OFControllerRole.ROLE_EQUAL;
            case ROLE_SLAVE:
                return OFControllerRole.ROLE_SLAVE;
            default:
                throw new IllegalArgumentException("unknown Nicira role value: " + roleReply.getRole());
        }
    }

    public static OFNiciraControllerRole ofRoleToNiciraRole(OFControllerRole role) {
        switch(role) {
            case ROLE_EQUAL:
                return OFNiciraControllerRole.ROLE_OTHER;
            case ROLE_MASTER:
                return OFNiciraControllerRole.ROLE_MASTER;
            case ROLE_SLAVE:
                return OFNiciraControllerRole.ROLE_SLAVE;
            default:
                throw new IllegalArgumentException("Unknown role: " + role);
        }
    }

}
