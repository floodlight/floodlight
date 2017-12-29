package net.floodlightcontroller.util;


import java.util.ArrayList;
import java.util.List;

import net.floodlightcontroller.core.IOFSwitch;

import org.projectfloodlight.openflow.protocol.OFAsyncGetReply;
import org.projectfloodlight.openflow.protocol.OFBarrierReply;
import org.projectfloodlight.openflow.protocol.OFBundleCtrlMsg;
import org.projectfloodlight.openflow.protocol.OFBundleCtrlType;
import org.projectfloodlight.openflow.protocol.OFEchoReply;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFGetConfigReply;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFQueueGetConfigReply;
import org.projectfloodlight.openflow.protocol.OFRoleReply;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;

/**
 * Tools to help work with OFMessages.
 * 
 * Compare OFMessage-extending objects (e.g. OFFlowMod, OFPacketIn)
 * where the XID does not matter. This is especially useful for
 * unit testing where the XID of the OFMessage might vary whereas
 * the expected OFMessgae must have a set XID.
 *
 * @author Ryan Izard <ryan.izard@bigswitch.com, rizard@g.clemson.edu>
 */

public class OFMessageUtils {

	/**
	 * Prevent instantiation
	 */
	private OFMessageUtils() {};

	/**
	 * Simple class to streamline the use of OFMessage's
	 * equalsIgnoreXid() and hashCodeIgnoreXid() functions.
	 * Use this class to wrap OFMessages prior to inserting
	 * them in containers where lookup or equality checks
	 * should not include the XID.
	 * 
	 * See {@link net.floodlightcontroller.util.OFMessageDamper}
	 * as an example where it's used to help cache OFMessages.
	 * @author rizard
	 */
	public static class OFMessageIgnoreXid {
	    private OFMessage m;
	    
	    private OFMessageIgnoreXid() {}
	    private OFMessageIgnoreXid(OFMessage m) {
	        this.m = m;
	    }
	    
	    /**
	     * Wrap an OFMessage to ignore the XID
	     * when checking for equality or computing
	     * the OFMessage's hash.
	     * @param m
	     * @return
	     */
	    public static OFMessageIgnoreXid of(OFMessage m) {
	        return new OFMessageIgnoreXid(m);
	    }
	    
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((m == null) ? 0 : m.hashCodeIgnoreXid());
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            OFMessageIgnoreXid other = (OFMessageIgnoreXid) obj;
            if (m == null) {
                if (other.m != null)
                    return false;
            } else if (!m.equalsIgnoreXid(other.m))
                return false;
            return true;
        }
	}
	
	/**
	 * Get the ingress port of a packet-in message. The manner in which
	 * this is done depends on the OpenFlow version. OF1.0 and 1.1 have
	 * a specific in_port field, while OF1.2+ store this information in
	 * the packet-in's match field.
	 * 
	 * @param pi, the OFPacketIn
	 * @return the ingress OFPort
	 */
	public static OFPort getInPort(OFPacketIn pi) {
		return pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT);
	}

	/**
	 * Set the ingress port of a packet-out message. The manner in which
	 * this is done depends on the OpenFlow version. OF1.0 thru 1.4 have
	 * a specific in_port field, while OF1.5+ store this information in
	 * the packet-out's match field.
	 * 
	 * @param pob, the OFPacketOut.Builder within which to set the in port
	 * @param in, the ingress OFPort
	 */
	public static void setInPort(OFPacketOut.Builder pob, OFPort in) {
		if (pob.getVersion().compareTo(OFVersion.OF_15) < 0) { 
			pob.setInPort(in);
		} else if (pob.getMatch() != null) {
			pob.getMatch().createBuilder()
			.setExact(MatchField.IN_PORT, in)
			.build();
		} else {
			pob.setMatch(OFFactories.getFactory(pob.getVersion())
					.buildMatch()
					.setExact(MatchField.IN_PORT, in)
					.build());
		}
	}

	/**
	 * Get the VLAN on which this packet-in message was received.
	 * @param pi, the OFPacketIn
	 * @return the VLAN
	 */
	public static OFVlanVidMatch getVlan(OFPacketIn pi) {
		return pi.getMatch().get(MatchField.VLAN_VID) == null ? OFVlanVidMatch.UNTAGGED : pi.getMatch().get(MatchField.VLAN_VID);
	}

	/**
	 * Writes an OFPacketOut message to a switch.
	 * 
	 * @param sw
	 *            The switch to write the PacketOut to.
	 * @param packetInMessage
	 *            The corresponding PacketIn.
	 * @param egressPort
	 *            The switchport to output the PacketOut.
	 */
	public static void writePacketOutForPacketIn(IOFSwitch sw,
			OFPacketIn packetInMessage, OFPort egressPort) {

		OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();

		// Set buffer_id, in_port, actions_len
		pob.setBufferId(packetInMessage.getBufferId());
		pob.setInPort(packetInMessage.getVersion().compareTo(OFVersion.OF_12) < 0 ? packetInMessage
				.getInPort() : packetInMessage.getMatch().get(
						MatchField.IN_PORT));

		// set actions
		List<OFAction> actions = new ArrayList<OFAction>(1);
		actions.add(sw.getOFFactory().actions().buildOutput()
				.setPort(egressPort).setMaxLen(0xffFFffFF).build());
		pob.setActions(actions);

		// set data - only if buffer_id == -1
		if (packetInMessage.getBufferId() == OFBufferId.NO_BUFFER) {
			byte[] packetData = packetInMessage.getData();
			pob.setData(packetData);
		}

		// and write it out
		sw.write(pob.build());
	}

	public static boolean isReplyForRequest(OFMessage request, OFMessage reply) {
		switch (request.getType()) {
			case BARRIER_REQUEST:
				return (reply instanceof OFBarrierReply);
			case BUNDLE_CONTROL:
				return isBundleCtrlReplyForRequest((OFBundleCtrlMsg) request, reply);
			case FEATURES_REQUEST:
				return (reply instanceof OFFeaturesReply);
			case GET_ASYNC_REQUEST:
				return (reply instanceof OFAsyncGetReply);
			case GET_CONFIG_REQUEST:
				return (reply instanceof OFGetConfigReply);
			case QUEUE_GET_CONFIG_REQUEST:
				return (reply instanceof OFQueueGetConfigReply);
			case ROLE_REQUEST:
				return (reply instanceof OFRoleReply);
			case STATS_REQUEST:
				return (reply instanceof OFStatsReply);
			case ECHO_REQUEST:
				return (reply instanceof OFEchoReply);
			default:
				return false;
		}
	}

	private static boolean isBundleCtrlReplyForRequest(OFBundleCtrlMsg request, OFMessage reply) {
		if (!(reply instanceof OFBundleCtrlMsg))
			return false;
		OFBundleCtrlMsg ctrlReply = (OFBundleCtrlMsg) reply;
		switch (request.getBundleCtrlType()) {
			case OPEN_REQUEST:
				return ctrlReply.getBundleCtrlType() == OFBundleCtrlType.OPEN_REPLY;
			case CLOSE_REQUEST:
				return ctrlReply.getBundleCtrlType() == OFBundleCtrlType.CLOSE_REPLY;
			case COMMIT_REQUEST:
				return ctrlReply.getBundleCtrlType() == OFBundleCtrlType.COMMIT_REPLY;
			default:
				return false;
		}
	}
}
