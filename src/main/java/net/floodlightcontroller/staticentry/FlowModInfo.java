package net.floodlightcontroller.staticentry;

import java.util.HashSet;
import java.util.Set;

import net.floodlightcontroller.util.MatchUtils;

import org.projectfloodlight.openflow.protocol.OFActionType;
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.actionid.OFActionId;
import org.projectfloodlight.openflow.protocol.actionid.OFActionIdExperimenter;
import org.projectfloodlight.openflow.protocol.actionid.OFActionIds;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.match.MatchFields;
import org.projectfloodlight.openflow.protocol.ver10.OFActionsVer10;
import org.projectfloodlight.openflow.types.OFValueType;

import com.google.common.collect.ImmutableSet;

public class FlowModInfo {
	private static final Set<FlowMatchProperty> matches = new HashSet<FlowMatchProperty>();
	private static final Set<FlowActionProperty> actions = new HashSet<FlowActionProperty>();
	//private static final Set<FlowInstructionProperty> instructions = new HashSet<FlowInstructionProperty>();
	
	private FlowModInfo() {}
	private static volatile FlowModInfo instance = null;
	public static synchronized FlowModInfo getInstance() {
		if (instance == null) {
			instance = new FlowModInfo();
		}
				
		for (MatchFields m : MatchFields.values()) {
			FlowMatchProperty fp = new FlowMatchProperty();
			fp.property = MatchUtils.getMatchField(m);
			fp.supportedOFVersions = ImmutableSet.copyOf(MatchUtils.getSupportedOFVersions(m));
			fp.usageNotes = ImmutableSet.of("add-in-usage-strings!");
			matches.add(fp);
		}
		for (OFActionType a : OFActionType.values()) {
			FlowActionProperty fp = new FlowActionProperty();
			//fp.property = a.g
		}
		
		return instance;
	}
	
	public static String toJson() {
		return "";
	}
	
	enum FP_VALUE_TYPE {
		NONE,
		NUMBER,
		MAC_ADDR,
		IPV4_ADDR,
		IPV4_ADDR_W_MASK,
		IPV6_ADDR,
		IPV6_ADDR_W_MASK,
		RESERVED_PORT,
		
	}
	private static class FlowMatchProperty {
		private Set<String> usageNotes;
		private MatchField<?> property;
		private Set<OFVersion> supportedOFVersions;
	}
	private static class FlowActionProperty {
		private Set<String> usageNotes;
		private OFActionId property;
		private Set<OFVersion> supportedOFVersions;
	}
}
