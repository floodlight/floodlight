package net.floodlightcontroller.util;

import java.util.ArrayList;
import java.util.List;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionClearActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionExperimenter;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionGotoTable;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteMetadata;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;

/**
 * Convert OFInstructions to and from dpctl/ofctl-style strings.
 * Used primarily by the static flow pusher to store and retreive
 * flow entries.
 * 
 * @author Ryan Izard <ryan.izard@bigswitch.com, rizard@g.clemson.edu>
 *
 */
public class InstructionUtils {
	public static final String STR_GOTO_TABLE = "instruction_goto_table";
	public static final String STR_WRITE_METADATA = "instruction_write_metadata";
	public static final String STR_WRITE_ACTIONS = "instruction_write_actions";
	public static final String STR_APPLY_ACTIONS = "instruction_apply_actions";
	public static final String STR_CLEAR_ACTIONS = "instruction_clear_actions";
	public static final String STR_GOTO_METER = "instruction_goto_meter";
	public static final String STR_EXPERIMENTER = "instruction_experimenter";

	private static final String STR_SUB_WRITE_METADATA_METADATA = "metadata";
	private static final String STR_SUB_WRITE_METADATA_MASK = "mask";
	private static final String STR_SUB_GOTO_METER_METER_ID = "meter_id";
	private static final String STR_SUB_EXPERIMENTER_VALUE = "experimenter";


	/** 
	 * Adds the instructions to the list of OFInstructions in the OFFlowMod. Any pre-existing
	 * instruction of the same type is replaced with OFInstruction inst.
	 * @param fmb, the flow mod to append the instruction to
	 * @param inst, the instuction to append
	 */
	public static void appendInstruction(OFFlowMod.Builder fmb, OFInstruction inst) {
		List<OFInstruction> newIl = new ArrayList<OFInstruction>();
		List<OFInstruction> oldIl = fmb.getInstructions();
		if (oldIl != null) { // keep any existing instructions that were added earlier
			newIl.addAll(fmb.getInstructions());
		}

		for (OFInstruction i : newIl) { // remove any duplicates. Only one of each instruction.
			if (i.getType() == inst.getType()) {
				newIl.remove(i);
			}
		}	
		newIl.add(inst);
		fmb.setInstructions(newIl);
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Convert an OFInstructionGotoTable to string form. The string will be formatted
	 * in a dpctl/ofctl-style syntax.
	 * @param inst; The instruction to convert to a string
	 * @param log
	 * @return
	 */
	public static String gotoTableToString(OFInstructionGotoTable inst, Logger log) {
		return Short.toString(inst.getTableId().getValue());
	}

	/**
	 * Convert the string representation of an OFInstructionGotoTable to
	 * an OFInstructionGotoTable. The instruction will be set within the
	 * OFFlowMod.Builder provided. Notice nothing is returned, but the
	 * side effect is the addition of an instruction in the OFFlowMod.Builder.
	 * @param fmb; The FMB in which to append the new instruction
	 * @param instStr; The string to parse the instruction from
	 * @param log
	 */
	public static void gotoTableFromString(OFFlowMod.Builder fmb, String instStr, Logger log) {
		if (instStr == null || instStr.equals("")) {
			return;
		}
		
		if (fmb.getVersion().compareTo(OFVersion.OF_11) < 0) {
			log.error("Goto Table Instruction not supported in OpenFlow 1.0");
			return;
		}
		
		// Split into pairs of key=value
		String[] keyValue = instStr.split("=");
		if (keyValue.length != 2) {
			throw new IllegalArgumentException("[Key, Value] " + keyValue + " does not have form 'key=value' parsing " + instStr);
		}

		OFInstructionGotoTable.Builder ib = OFFactories.getFactory(fmb.getVersion()).instructions().buildGotoTable();
		ib.setTableId(TableId.of(Integer.parseInt(keyValue[1]))).build();

		log.debug("Appending GotoTable instruction: {}", ib.build());
		appendInstruction(fmb, ib.build());
		log.debug("All instructions after append: {}", fmb.getInstructions());	}

	/**
	 * Convert an OFInstructionMetadata to string form. The string will be formatted
	 * in a dpctl/ofctl-style syntax.
	 * @param inst; The instruction to convert to a string
	 * @param log
	 * @return
	 */
	public static String writeMetadataToString(OFInstructionWriteMetadata inst, Logger log) {
		/* 
		 * U64.toString() looks like it formats with a leading 0x. getLong() will allow us to work with just the value
		 * For the rest api though, will the user provide a hex value or a long? I'd guess a hex value would be more useful.
		 */
		return STR_SUB_WRITE_METADATA_METADATA + "=" + Long.toString(inst.getMetadata().getValue()) + "," + STR_SUB_WRITE_METADATA_MASK + "=" + Long.toString(inst.getMetadataMask().getValue());
	}
	
	/**
	 * Convert the string representation of an OFInstructionMetadata to
	 * an OFInstructionMetadata. The instruction will be set within the
	 * OFFlowMod.Builder provided. Notice nothing is returned, but the
	 * side effect is the addition of an instruction in the OFFlowMod.Builder.
	 * @param fmb; The FMB in which to append the new instruction
	 * @param instStr; The string to parse the instruction from
	 * @param log
	 */
	public static void writeMetadataFromString(OFFlowMod.Builder fmb, String inst, Logger log) {
		if (inst == null || inst.equals("")) {
			return;
		}
		
		if (fmb.getVersion().compareTo(OFVersion.OF_11) < 0) {
			log.error("Write Metadata Instruction not supported in OpenFlow 1.0");
			return;
		}
		
		// Split into pairs of key=value
		String[] tokens = inst.split(",");
		if (tokens.length != 2) {
			throw new IllegalArgumentException("Tokens " + tokens + " does not have form '[t1, t2]' parsing " + inst);
		}

		OFInstructionWriteMetadata.Builder ib = OFFactories.getFactory(fmb.getVersion()).instructions().buildWriteMetadata();

		// Process tokens (should be metadata or its mask)
		for (int i = 0; i < tokens.length; i++) {
			String[] keyValue = tokens[0].split("=");	
			if (keyValue.length != 2) {
				throw new IllegalArgumentException("[Key, Value] " + keyValue + " does not have form 'key=value' parsing " + inst);
			}
			switch (keyValue[0]) {
			case STR_SUB_WRITE_METADATA_METADATA:
				ib.setMetadata(U64.of(Long.parseLong(keyValue[1])));
				break;
			case STR_SUB_WRITE_METADATA_MASK:
				ib.setMetadataMask(U64.of(Long.parseLong(keyValue[1])));
			default:
				log.error("Invalid String key for OFInstructionWriteMetadata: {}", keyValue[0]);
			}
		}
		log.debug("Appending WriteMetadata instruction: {}", ib.build());
		appendInstruction(fmb, ib.build());
		log.debug("All instructions after append: {}", fmb.getInstructions());
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Convert an OFInstructionWriteActions to string form. The string will be formatted
	 * in a dpctl/ofctl-style syntax.
	 * @param inst; The instruction to convert to a string
	 * @param log
	 * @return
	 */
	public static String writeActionsToString(OFInstructionWriteActions inst, Logger log) throws Exception {
		return ActionUtils.actionsToString(inst.getActions(), log);
	}

	/**
	 * Convert the string representation of an OFInstructionWriteActions to
	 * an OFInstructionWriteActions. The instruction will be set within the
	 * OFFlowMod.Builder provided. Notice nothing is returned, but the
	 * side effect is the addition of an instruction in the OFFlowMod.Builder.
	 * @param fmb; The FMB in which to append the new instruction
	 * @param instStr; The string to parse the instruction from
	 * @param log
	 */
	public static void writeActionsFromString(OFFlowMod.Builder fmb, String inst, Logger log) {
		
		if (fmb.getVersion().compareTo(OFVersion.OF_11) < 0) {
			log.error("Write Actions Instruction not supported in OpenFlow 1.0");
			return;
		}
		
		OFFlowMod.Builder tmpFmb = OFFactories.getFactory(fmb.getVersion()).buildFlowModify(); // ActionUtils.fromString() will use setActions(), which should not be used for OF1.3; use temp to avoid overwriting any applyActions data
		OFInstructionWriteActions.Builder ib = OFFactories.getFactory(fmb.getVersion()).instructions().buildWriteActions();
		ActionUtils.fromString(tmpFmb, inst, log);
		ib.setActions(tmpFmb.getActions());
		log.debug("Appending WriteActions instruction: {}", ib.build());
		appendInstruction(fmb, ib.build());
		log.debug("All instructions after append: {}", fmb.getInstructions());	}

	///////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Convert an OFInstructionApplyActions to string form. The string will be formatted
	 * in a dpctl/ofctl-style syntax.
	 * @param inst; The instruction to convert to a string
	 * @param log
	 * @return
	 */
	public static String applyActionsToString(OFInstructionApplyActions inst, Logger log) throws Exception {
		return ActionUtils.actionsToString(inst.getActions(), log);
	}

	/**
	 * Convert the string representation of an OFInstructionApplyActions to
	 * an OFInstructionApplyActions. The instruction will be set within the
	 * OFFlowMod.Builder provided. Notice nothing is returned, but the
	 * side effect is the addition of an instruction in the OFFlowMod.Builder.
	 * @param fmb; The FMB in which to append the new instruction
	 * @param instStr; The string to parse the instruction from
	 * @param log
	 */
	public static void applyActionsFromString(OFFlowMod.Builder fmb, String inst, Logger log) {
		
		if (fmb.getVersion().compareTo(OFVersion.OF_11) < 0) {
			log.error("Apply Actions Instruction not supported in OpenFlow 1.0");
			return;
		}
		
		OFFlowMod.Builder tmpFmb = OFFactories.getFactory(fmb.getVersion()).buildFlowModify();
		OFInstructionApplyActions.Builder ib = OFFactories.getFactory(fmb.getVersion()).instructions().buildApplyActions();
		ActionUtils.fromString(tmpFmb, inst, log);
		ib.setActions(tmpFmb.getActions());
		log.debug("Appending ApplyActions instruction: {}", ib.build());
		appendInstruction(fmb, ib.build());
		log.debug("All instructions after append: {}", fmb.getInstructions());	}

	///////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Convert an OFInstructionClearActions to string form. The string will be formatted
	 * in a dpctl/ofctl-style syntax.
	 * @param inst; The instruction to convert to a string
	 * @param log
	 * @return
	 */
	public static String clearActionsToString(OFInstructionClearActions inst, Logger log) {
		return ""; // No data for this instruction. The presence of it's key indicates it is to be applied.
	}

	/**
	 * Convert the string representation of an OFInstructionClearActions to
	 * an OFInstructionClearActions. The instruction will be set within the
	 * OFFlowMod.Builder provided. Notice nothing is returned, but the
	 * side effect is the addition of an instruction in the OFFlowMod.Builder.
	 * @param fmb; The FMB in which to append the new instruction
	 * @param instStr; The string to parse the instruction from
	 * @param log
	 */
	public static void clearActionsFromString(OFFlowMod.Builder fmb, String inst, Logger log) {
		
		if (fmb.getVersion().compareTo(OFVersion.OF_11) < 0) {
			log.error("Clear Actions Instruction not supported in OpenFlow 1.0");
			return;
		}
		
		if (inst != null && inst.isEmpty()) {
			OFInstructionClearActions i = OFFactories.getFactory(fmb.getVersion()).instructions().clearActions();
			log.debug("Appending ClearActions instruction: {}", i);
			appendInstruction(fmb, i);
			log.debug("All instructions after append: {}", fmb.getInstructions());		
		} else {
			log.error("Got non-empty or null string, but ClearActions should not have any String sub-fields: {}", inst);
		}
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Convert an OFInstructionMeter to string form. The string will be formatted
	 * in a dpctl/ofctl-style syntax.
	 * @param inst; The instruction to convert to a string
	 * @param log
	 * @return
	 */
	public static String meterToString(OFInstructionMeter inst, Logger log) {
		return STR_SUB_GOTO_METER_METER_ID + "=" + Long.toString(inst.getMeterId());
	}

	/**
	 * Convert the string representation of an OFInstructionMeter to
	 * an OFInstructionMeter. The instruction will be set within the
	 * OFFlowMod.Builder provided. Notice nothing is returned, but the
	 * side effect is the addition of an instruction in the OFFlowMod.Builder.
	 * @param fmb; The FMB in which to append the new instruction
	 * @param instStr; The string to parse the instruction from
	 * @param log
	 */
	public static void meterFromString(OFFlowMod.Builder fmb, String inst, Logger log) {
		if (inst == null || inst.isEmpty()) {
			return;
		}
		
		if (fmb.getVersion().compareTo(OFVersion.OF_13) < 0) {
			log.error("Goto Meter Instruction not supported in OpenFlow 1.0, 1.1, or 1.2");
			return;
		}

		OFInstructionMeter.Builder ib = OFFactories.getFactory(fmb.getVersion()).instructions().buildMeter();

		String[] keyValue = inst.split("=");	
		if (keyValue.length != 2) {
			throw new IllegalArgumentException("[Key, Value] " + keyValue + " does not have form 'key=value' parsing " + inst);
		}
		switch (keyValue[0]) {
		case STR_SUB_GOTO_METER_METER_ID:
			ib.setMeterId(Long.parseLong(keyValue[1]));
			break;
		default:
			log.error("Invalid String key for OFInstructionMeter: {}", keyValue[0]);
		}

		log.debug("Appending (Goto)Meter instruction: {}", ib.build());
		appendInstruction(fmb, ib.build());
		log.debug("All instructions after append: {}", fmb.getInstructions());	}

	///////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Convert an OFInstructionExperimenter to string form. The string will be formatted
	 * in a dpctl/ofctl-style syntax.
	 * @param inst; The instruction to convert to a string
	 * @param log
	 * @return
	 */
	public static String experimenterToString(OFInstructionExperimenter inst, Logger log) {
		return STR_SUB_EXPERIMENTER_VALUE  + "=" + Long.toString(inst.getExperimenter());
	}

	/**
	 * Convert the string representation of an OFInstructionExperimenter to
	 * an OFInstructionExperimenter. The instruction will be set within the
	 * OFFlowMod.Builder provided. Notice nothing is returned, but the
	 * side effect is the addition of an instruction in the OFFlowMod.Builder.
	 * @param fmb; The FMB in which to append the new instruction
	 * @param instStr; The string to parse the instruction from
	 * @param log
	 */
	public static void experimenterFromString(OFFlowMod.Builder fmb, String inst, Logger log) {
		/* TODO This is a no-op right now. */
		
		/*
		if (inst == null || inst.equals("")) {
			return; // TODO @Ryan quietly fail?
		}

		OFInstructionExperimenter.Builder ib = OFFactories.getFactory(fmb.getVersion()).instructions().buildExperimenter();

		String[] keyValue = inst.split("=");	
		if (keyValue.length != 2) {
			throw new IllegalArgumentException("[Key, Value] " + keyValue + " does not have form 'key=value' parsing " + inst);
		}
		switch (keyValue[0]) {
		case STR_SUB_GOTO_METER_METER_ID:
			ib.setExperimenter(Long.parseLong(keyValue[1]));
			break;
		default:
			log.error("Invalid String key for OFInstructionExperimenter: {}", keyValue[0]);
		}

		appendInstruction(fmb, ib.build());
		 */
	}


}
