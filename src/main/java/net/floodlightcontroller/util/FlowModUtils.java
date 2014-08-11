package net.floodlightcontroller.util;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowDelete;
import org.projectfloodlight.openflow.protocol.OFFlowDeleteStrict;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModify;
import org.projectfloodlight.openflow.protocol.OFFlowModifyStrict;
import org.projectfloodlight.openflow.protocol.OFVersion;

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
		return b.setActions(fm.getActions())
				.setBufferId(fm.getBufferId())
				.setCookie(fm.getCookie())
				.setCookieMask(fm.getCookieMask())
				.setFlags(fm.getFlags())
				.setHardTimeout(fm.getHardTimeout())
				.setIdleTimeout(fm.getIdleTimeout())
				.setInstructions(fm.getInstructions())
				.setMatch(fm.getMatch())
				.setOutGroup(fm.getOutGroup())
				.setOutPort(fm.getOutPort())
				.setPriority(fm.getPriority())
				.setTableId(fm.getTableId())
				.setXid(fm.getXid())
				.build();
	}

	public static OFFlowDelete toFlowDelete(OFFlowMod fm) {
		OFVersion version = fm.getVersion();
		OFFlowDelete.Builder b = OFFactories.getFactory(version).buildFlowDelete();
		return b.setActions(fm.getActions())
				.setBufferId(fm.getBufferId())
				.setCookie(fm.getCookie())
				.setCookieMask(fm.getCookieMask())
				.setFlags(fm.getFlags())
				.setHardTimeout(fm.getHardTimeout())
				.setIdleTimeout(fm.getIdleTimeout())
				.setInstructions(fm.getInstructions())
				.setMatch(fm.getMatch())
				.setOutGroup(fm.getOutGroup())
				.setOutPort(fm.getOutPort())
				.setPriority(fm.getPriority())
				.setTableId(fm.getTableId())
				.setXid(fm.getXid())
				.build();
	}

	public static OFFlowDeleteStrict toFlowDeleteStrict(OFFlowMod fm) {
		OFVersion version = fm.getVersion();
		OFFlowDeleteStrict.Builder b = OFFactories.getFactory(version).buildFlowDeleteStrict();
		return b.setActions(fm.getActions())
				.setBufferId(fm.getBufferId())
				.setCookie(fm.getCookie())
				.setCookieMask(fm.getCookieMask())
				.setFlags(fm.getFlags())
				.setHardTimeout(fm.getHardTimeout())
				.setIdleTimeout(fm.getIdleTimeout())
				.setInstructions(fm.getInstructions())
				.setMatch(fm.getMatch())
				.setOutGroup(fm.getOutGroup())
				.setOutPort(fm.getOutPort())
				.setPriority(fm.getPriority())
				.setTableId(fm.getTableId())
				.setXid(fm.getXid())
				.build();
	}

	public static OFFlowModify toFlowModify(OFFlowMod fm) {
		OFVersion version = fm.getVersion();
		OFFlowModify.Builder b = OFFactories.getFactory(version).buildFlowModify();
		return b.setActions(fm.getActions())
				.setBufferId(fm.getBufferId())
				.setCookie(fm.getCookie())
				.setCookieMask(fm.getCookieMask())
				.setFlags(fm.getFlags())
				.setHardTimeout(fm.getHardTimeout())
				.setIdleTimeout(fm.getIdleTimeout())
				.setInstructions(fm.getInstructions())
				.setMatch(fm.getMatch())
				.setOutGroup(fm.getOutGroup())
				.setOutPort(fm.getOutPort())
				.setPriority(fm.getPriority())
				.setTableId(fm.getTableId())
				.setXid(fm.getXid())
				.build();
	}

	public static OFFlowModifyStrict toFlowModifyStrict(OFFlowMod fm) {
		OFVersion version = fm.getVersion();
		OFFlowModifyStrict.Builder b = OFFactories.getFactory(version).buildFlowModifyStrict();
		return b.setActions(fm.getActions())
				.setBufferId(fm.getBufferId())
				.setCookie(fm.getCookie())
				.setCookieMask(fm.getCookieMask())
				.setFlags(fm.getFlags())
				.setHardTimeout(fm.getHardTimeout())
				.setIdleTimeout(fm.getIdleTimeout())
				.setInstructions(fm.getInstructions())
				.setMatch(fm.getMatch())
				.setOutGroup(fm.getOutGroup())
				.setOutPort(fm.getOutPort())
				.setPriority(fm.getPriority())
				.setTableId(fm.getTableId())
				.setXid(fm.getXid())
				.build();
	}
}
