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
	public static void gotoTableFromString(OFFlowMod.Builder fmb, String inst, Logger log) {
		if (inst == null || inst.equals("")) {
			return;
		}

		if (fmb.getVersion().compareTo(OFVersion.OF_11) < 0) {
			log.error("Goto Table Instruction not supported in OpenFlow 1.0");
			return;
		}

		OFInstructionGotoTable.Builder ib = OFFactories.getFactory(fmb.getVersion()).instructions().buildGotoTable();

		// Get the table ID
		if (inst.startsWith("0x")) {
			ib.setTableId(TableId.of(Integer.parseInt(inst.replaceFirst("0x", ""), 16)));
		} else {
			ib.setTableId(TableId.of(Integer.parseInt(inst))).build();
		}

		log.debug("Appending GotoTable instruction: {}", ib.build());
		appendInstruction(fmb, ib.build());
		log.debug("All instructions after append: {}", fmb.getInstructions());	
	}

	/**
	 * Convert an OFInstructionMetadata to string form. The string will be formatted
	 * in a dpctl/ofctl-style syntax.
	 * @param inst; The instruction to convert to a string
	 * @param log
	 * @return
	 */
	public static String writeMetadataToString(OFInstructionWriteMetadata inst, Logger log) {
		/* 
		 * U64.toString() formats with a leading 0x
		 */
		if (inst.getMetadataMask().equals(U64.NO_MASK)) { // don't give the mask if it's all 1's --> omit "/"
			return inst.getMetadata().toString();
		} else {
			return inst.getMetadata().toString() + "/" + inst.getMetadataMask().toString();
		}
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

		OFInstructionWriteMetadata.Builder ib = OFFactories.getFactory(fmb.getVersion()).instructions().buildWriteMetadata();

		// Process tokens (should be metadata or its mask)
		String[] keyValue = inst.split("/");	
		if (keyValue.length > 2) {
			throw new IllegalArgumentException("[Metadata, Mask] " + keyValue + " does not have form 'metadata/mask' or 'metadata' for parsing " + inst);
		} else if (keyValue.length == 1) {
			log.debug("No mask detected in OFInstructionWriteMetaData string.");
		} else if (keyValue.length == 2) {
			log.debug("Detected mask in OFInstructionWriteMetaData string.");
		}

		// Get the metadata
		if (keyValue[0].startsWith("0x")) {
			ib.setMetadata(U64.of(Long.valueOf(keyValue[0].replaceFirst("0x", ""), 16)));
		} else {
			ib.setMetadata(U64.of(Long.valueOf(keyValue[0])));
		}

		// Get the optional mask
		if (keyValue.length == 2) {
			if (keyValue[1].startsWith("0x")) {
				ib.setMetadataMask(U64.of(Long.valueOf(keyValue[1].replaceFirst("0x", ""), 16)));
			} else {
				ib.setMetadataMask(U64.of(Long.valueOf(keyValue[1])));
			}
		} else {
			ib.setMetadataMask(U64.NO_MASK);
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

		if (inst != null && inst.trim().isEmpty()) { /* Allow the empty string, since this is what specifies clear (i.e. key clear does not have any defined values). */
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
		return Long.toString(inst.getMeterId());
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

		if (inst.startsWith("0x")) {
			ib.setMeterId(Long.valueOf(inst.replaceFirst("0x", ""), 16));
		} else {
			ib.setMeterId(Long.valueOf(inst));
		}		

		log.debug("Appending (Goto)Meter instruction: {}", ib.build());
		appendInstruction(fmb, ib.build());
		log.debug("All instructions after append: {}", fmb.getInstructions());
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Convert an OFInstructionExperimenter to string form. The string will be formatted
	 * in a dpctl/ofctl-style syntax.
	 * @param inst; The instruction to convert to a string
	 * @param log
	 * @return
	 */
	public static String experimenterToString(OFInstructionExperimenter inst, Logger log) {
		return Long.toString(inst.getExperimenter());
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
