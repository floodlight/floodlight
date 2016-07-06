package net.floodlightcontroller.util;

import java.util.Collections;
import java.util.List;

import net.floodlightcontroller.core.IOFSwitch;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowDelete;
import org.projectfloodlight.openflow.protocol.OFFlowDeleteStrict;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModify;
import org.projectfloodlight.openflow.protocol.OFFlowModifyStrict;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;

/**
 * Convert an OFFlowMod to a specific OFFlowMod-OFFlowModCommand.
 * These function as setCommand(OFFlowModCommand) methods for an OFFlowMod.
 * Used initially in the static flow pusher, but will likely find merit elsewhere.
 * 
 * Other useful FlowMod utility functions and constants are also included.
 *
 * @author Ryan Izard <ryan.izard@bigswitch.com, rizard@g.clemson.edu>
 */
public class FlowModUtils {
	public static final int INFINITE_TIMEOUT = 0;

	public static final int PRIORITY_MAX = 32768;
	public static final int PRIORITY_VERY_HIGH = 28672;
	public static final int PRIORITY_HIGH = 24576;
	public static final int PRIORITY_MED_HIGH = 20480;
	public static final int PRIORITY_MED = 16384;
	public static final int PRIORITY_MED_LOW = 12288;
	public static final int PRIORITY_LOW = 8192;
	public static final int PRIORITY_VERY_LOW = 4096;
	public static final int PRIORITY_MIN = 0;

	public static OFFlowAdd toFlowAdd(OFFlowMod fm) {
		OFVersion version = fm.getVersion();
		OFFlowAdd.Builder b = OFFactories.getFactory(version).buildFlowAdd();

		if (!(fm instanceof OFFlowDelete) && !(fm instanceof OFFlowDeleteStrict)) {
			b.setActions(fm.getActions());
		}
		if (b.getVersion().compareTo(OFVersion.OF_10) == 0) {
			return b.setBufferId(fm.getBufferId())
					.setCookie(fm.getCookie())
					// cookie-mask not supported
					.setFlags(fm.getFlags())
					.setHardTimeout(fm.getHardTimeout())
					.setIdleTimeout(fm.getIdleTimeout())
					// instructions not supported
					.setMatch(fm.getMatch())
					// out-group not supported
					.setOutPort(fm.getOutPort())
					.setPriority(fm.getPriority())
					// table-id not supported
					.setXid(fm.getXid())
					.build();
		} else {
			return b.setBufferId(fm.getBufferId())
					.setCookie(fm.getCookie())
					.setCookieMask(fm.getCookieMask()) // added in OF1.1
					.setFlags(fm.getFlags())
					.setHardTimeout(fm.getHardTimeout())
					.setIdleTimeout(fm.getIdleTimeout())
					.setInstructions(fm.getInstructions()) // added in OF1.1
					.setMatch(fm.getMatch())
					.setOutGroup(fm.getOutGroup()) // added in OF1.1
					.setOutPort(fm.getOutPort())
					.setPriority(fm.getPriority())
					.setTableId(fm.getTableId())
					.setXid(fm.getXid())
					.build();
		}
	}

	public static OFFlowDelete toFlowDelete(OFFlowMod fm) {
		OFVersion version = fm.getVersion();
		OFFlowDelete.Builder b = OFFactories.getFactory(version).buildFlowDelete();
		/* ignore actions */
		if (b.getVersion().compareTo(OFVersion.OF_10) == 0) {
			return b.setBufferId(fm.getBufferId())
					.setCookie(fm.getCookie())
					// cookie-mask not supported
					.setFlags(fm.getFlags())
					.setHardTimeout(fm.getHardTimeout())
					.setIdleTimeout(fm.getIdleTimeout())
					// instructions not supported
					.setMatch(fm.getMatch())
					// out-group not supported
					.setOutPort(fm.getOutPort())
					.setPriority(fm.getPriority())
					// table-id not supported
					.setXid(fm.getXid())
					.build();
		} else {
			return b.setBufferId(fm.getBufferId())
					.setCookie(fm.getCookie())
					.setCookieMask(fm.getCookieMask()) // added in OF1.1
					.setFlags(fm.getFlags())
					.setHardTimeout(fm.getHardTimeout())
					.setIdleTimeout(fm.getIdleTimeout())
					.setInstructions(fm.getInstructions()) // added in OF1.1
					.setMatch(fm.getMatch())
					.setOutGroup(fm.getOutGroup()) // added in OF1.1
					.setOutPort(fm.getOutPort())
					.setPriority(fm.getPriority())
					.setTableId(fm.getTableId())
					.setXid(fm.getXid())
					.build();
		}
	}

	public static OFFlowDeleteStrict toFlowDeleteStrict(OFFlowMod fm) {
		OFVersion version = fm.getVersion();
		OFFlowDeleteStrict.Builder b = OFFactories.getFactory(version).buildFlowDeleteStrict();
		/* ignore actions */
		if (b.getVersion().compareTo(OFVersion.OF_10) == 0) {
			return b.setBufferId(fm.getBufferId())
					.setCookie(fm.getCookie())
					// cookie-mask not supported
					.setFlags(fm.getFlags())
					.setHardTimeout(fm.getHardTimeout())
					.setIdleTimeout(fm.getIdleTimeout())
					// instructions not supported
					.setMatch(fm.getMatch())
					// out-group not supported
					.setOutPort(fm.getOutPort())
					.setPriority(fm.getPriority())
					// table-id not supported
					.setXid(fm.getXid())
					.build();
		} else {
			return b.setBufferId(fm.getBufferId())
					.setCookie(fm.getCookie())
					.setCookieMask(fm.getCookieMask()) // added in OF1.1
					.setFlags(fm.getFlags())
					.setHardTimeout(fm.getHardTimeout())
					.setIdleTimeout(fm.getIdleTimeout())
					.setInstructions(fm.getInstructions()) // added in OF1.1
					.setMatch(fm.getMatch())
					.setOutGroup(fm.getOutGroup()) // added in OF1.1
					.setOutPort(fm.getOutPort())
					.setPriority(fm.getPriority())
					.setTableId(fm.getTableId())
					.setXid(fm.getXid())
					.build();
		}
	}

	public static OFFlowModify toFlowModify(OFFlowMod fm) {
		OFVersion version = fm.getVersion();
		OFFlowModify.Builder b = OFFactories.getFactory(version).buildFlowModify();
		if (!(fm instanceof OFFlowDelete) && !(fm instanceof OFFlowDeleteStrict)) {
			b.setActions(fm.getActions());
		}
		if (b.getVersion().compareTo(OFVersion.OF_10) == 0) {
			return b.setBufferId(fm.getBufferId())
					.setCookie(fm.getCookie())
					// cookie-mask not supported
					.setFlags(fm.getFlags())
					.setHardTimeout(fm.getHardTimeout())
					.setIdleTimeout(fm.getIdleTimeout())
					// instructions not supported
					.setMatch(fm.getMatch())
					// out-group not supported
					.setOutPort(fm.getOutPort())
					.setPriority(fm.getPriority())
					// table-id not supported
					.setXid(fm.getXid())
					.build();
		} else {
			return b.setBufferId(fm.getBufferId())
					.setCookie(fm.getCookie())
					.setCookieMask(fm.getCookieMask()) // added in OF1.1
					.setFlags(fm.getFlags())
					.setHardTimeout(fm.getHardTimeout())
					.setIdleTimeout(fm.getIdleTimeout())
					.setInstructions(fm.getInstructions()) // added in OF1.1
					.setMatch(fm.getMatch())
					.setOutGroup(fm.getOutGroup()) // added in OF1.1
					.setOutPort(fm.getOutPort())
					.setPriority(fm.getPriority())
					.setTableId(fm.getTableId())
					.setXid(fm.getXid())
					.build();
		}
	}

	public static OFFlowModifyStrict toFlowModifyStrict(OFFlowMod fm) {
		OFVersion version = fm.getVersion();
		OFFlowModifyStrict.Builder b = OFFactories.getFactory(version).buildFlowModifyStrict();
		if (!(fm instanceof OFFlowDelete) && !(fm instanceof OFFlowDeleteStrict)) {
			b.setActions(fm.getActions());
		}
		if (b.getVersion().compareTo(OFVersion.OF_10) == 0) {
			return b.setBufferId(fm.getBufferId())
					.setCookie(fm.getCookie())
					// cookie-mask not supported
					.setFlags(fm.getFlags())
					.setHardTimeout(fm.getHardTimeout())
					.setIdleTimeout(fm.getIdleTimeout())
					// instructions not supported
					.setMatch(fm.getMatch())
					// out-group not supported
					.setOutPort(fm.getOutPort())
					.setPriority(fm.getPriority())
					// table-id not supported
					.setXid(fm.getXid())
					.build();
		} else {
			return b.setBufferId(fm.getBufferId())
					.setCookie(fm.getCookie())
					.setCookieMask(fm.getCookieMask()) // added in OF1.1
					.setFlags(fm.getFlags())
					.setHardTimeout(fm.getHardTimeout())
					.setIdleTimeout(fm.getIdleTimeout())
					.setInstructions(fm.getInstructions()) // added in OF1.1
					.setMatch(fm.getMatch())
					.setOutGroup(fm.getOutGroup()) // added in OF1.1
					.setOutPort(fm.getOutPort())
					.setPriority(fm.getPriority())
					.setTableId(fm.getTableId())
					.setXid(fm.getXid())
					.build();
		}
	}
	
	/**
	 * Sets the actions in fmb according to the sw version.
	 * 
	 * @param fmb the FlowMod Builder that is being built
	 * @param actions the actions to set
	 * @param sw the switch that will receive the FlowMod
	 */
	public static void setActions(OFFlowMod.Builder fmb,
			List<OFAction> actions, IOFSwitch sw) {
		if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_11) >= 0) {
			// Instructions are used starting in OF 1.1
			fmb.setInstructions(Collections.singletonList((OFInstruction) sw
					.getOFFactory().instructions().applyActions(actions)));
		} else {
			// OF 1.0 only supports actions
			fmb.setActions(actions);
		}
	}
}
