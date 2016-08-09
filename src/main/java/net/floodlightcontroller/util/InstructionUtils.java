package net.floodlightcontroller.util;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFInstructionType;
import org.projectfloodlight.openflow.protocol.OFOxsList;
import org.projectfloodlight.openflow.protocol.OFStatTriggerFlags;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionClearActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionExperimenter;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionGotoTable;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionStatTrigger;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionWriteMetadata;
import org.projectfloodlight.openflow.protocol.oxs.OFOxs;
import org.projectfloodlight.openflow.protocol.oxs.OFOxsByteCount;
import org.projectfloodlight.openflow.protocol.oxs.OFOxsDuration;
import org.projectfloodlight.openflow.protocol.oxs.OFOxsFlowCount;
import org.projectfloodlight.openflow.protocol.oxs.OFOxsIdleTime;
import org.projectfloodlight.openflow.protocol.oxs.OFOxsPacketCount;
import org.projectfloodlight.openflow.protocol.stat.StatField;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U32;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonGenerator.Feature;

/**
 * Convert OFInstructions to and from dpctl/ofctl-style strings.
 * Used primarily by the static flow pusher to store and retreive
 * flow entries.
 * 
 * @author Ryan Izard <ryan.izard@bigswitch.com, rizard@g.clemson.edu>
 *
 */
public class InstructionUtils {
	private static final Logger log = LoggerFactory.getLogger(InstructionUtils.class);

	public static final String STR_GOTO_TABLE = "instruction_goto_table";
	public static final String STR_WRITE_METADATA = "instruction_write_metadata";
	public static final String STR_WRITE_ACTIONS = "instruction_write_actions";
	public static final String STR_APPLY_ACTIONS = "instruction_apply_actions";
	public static final String STR_CLEAR_ACTIONS = "instruction_clear_actions";
	public static final String STR_GOTO_METER = "instruction_goto_meter";
	public static final String STR_EXPERIMENTER = "instruction_experimenter";
	public static final String STR_DEPRECATED = "instruction_deprecated";
	public static final String STR_STAT_TRIGGER = "instruction_stat_trigger";
	
	private static final JsonFactory jsonFactory = new JsonFactory();
	private static final String JSON_EMPTY_OBJECT = "{}";

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

	/**
	 * Get string name of OFInstructionType
	 * @param t
	 * @return
	 */
	public static String getInstructionName(OFInstructionType t) {
		switch (t) {
		case APPLY_ACTIONS:
			return STR_APPLY_ACTIONS;
		case CLEAR_ACTIONS:
			return STR_CLEAR_ACTIONS;
		case DEPRECATED:
			return STR_DEPRECATED;
		case EXPERIMENTER:
			return STR_EXPERIMENTER;
		case GOTO_TABLE:
			return STR_GOTO_TABLE;
		case METER:
			return STR_GOTO_METER;
		case STAT_TRIGGER:
			return STR_STAT_TRIGGER;
		case WRITE_ACTIONS:
			return STR_WRITE_ACTIONS;
		case WRITE_METADATA:
			return STR_WRITE_METADATA;	
		}
		return "";
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Convert an OFInstructionGotoTable to string form. The string will be formatted
	 * in a dpctl/ofctl-style syntax.
	 * @param inst; The instruction to convert to a string
	 * @return
	 */
	public static String gotoTableToString(OFInstructionGotoTable inst) {
		return Short.toString(inst.getTableId().getValue());
	}

	/**
	 * Convert the string representation of an OFInstructionGotoTable to
	 * an OFInstructionGotoTable. The instruction will be set within the
	 * OFFlowMod.Builder provided. Notice nothing is returned, but the
	 * side effect is the addition of an instruction in the OFFlowMod.Builder.
	 * @param fmb; The FMB in which to append the new instruction
	 * @param instStr; The string to parse the instruction from
	 */
	public static void gotoTableFromString(OFFlowMod.Builder fmb, String inst) {
		if (inst == null || inst.equals("")) {
			return;
		}

		if (fmb.getVersion().compareTo(OFVersion.OF_11) < 0) {
			log.error("Goto Table Instruction not supported in OpenFlow 1.0");
			return;
		}

		OFInstructionGotoTable.Builder ib = OFFactories.getFactory(fmb.getVersion()).instructions().buildGotoTable();

		// Get the table ID
		ib.setTableId(TableId.of(ParseUtils.parseHexOrDecInt(inst)));

		log.debug("Appending GotoTable instruction: {}", ib.build());
		appendInstruction(fmb, ib.build());
		log.debug("All instructions after append: {}", fmb.getInstructions());	
	}

	/**
	 * Convert an OFInstructionMetadata to string form. The string will be formatted
	 * in a dpctl/ofctl-style syntax.
	 * @param inst; The instruction to convert to a string
	 * @return
	 */
	public static String writeMetadataToString(OFInstructionWriteMetadata inst) {
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
	 */
	public static void writeMetadataFromString(OFFlowMod.Builder fmb, String inst) {
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
		ib.setMetadata(U64.of(ParseUtils.parseHexOrDecLong(keyValue[0])));

		// Get the optional mask
		if (keyValue.length == 2) {
			ib.setMetadataMask(U64.of(ParseUtils.parseHexOrDecLong(keyValue[1])));
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
	 * @return
	 */
	public static String writeActionsToString(OFInstructionWriteActions inst) throws Exception {
		return ActionUtils.actionsToString(inst.getActions());
	}

	/**
	 * Convert the string representation of an OFInstructionWriteActions to
	 * an OFInstructionWriteActions. The instruction will be set within the
	 * OFFlowMod.Builder provided. Notice nothing is returned, but the
	 * side effect is the addition of an instruction in the OFFlowMod.Builder.
	 * @param fmb; The FMB in which to append the new instruction
	 * @param instStr; The string to parse the instruction from
	 */
	public static void writeActionsFromString(OFFlowMod.Builder fmb, String inst) {

		if (fmb.getVersion().compareTo(OFVersion.OF_11) < 0) {
			log.error("Write Actions Instruction not supported in OpenFlow 1.0");
			return;
		}

		OFFlowMod.Builder tmpFmb = OFFactories.getFactory(fmb.getVersion()).buildFlowModify(); // ActionUtils.fromString() will use setActions(), which should not be used for OF1.3; use temp to avoid overwriting any applyActions data
		OFInstructionWriteActions.Builder ib = OFFactories.getFactory(fmb.getVersion()).instructions().buildWriteActions();
		ActionUtils.fromString(tmpFmb, inst);
		ib.setActions(tmpFmb.getActions());
		log.debug("Appending WriteActions instruction: {}", ib.build());
		appendInstruction(fmb, ib.build());
		log.debug("All instructions after append: {}", fmb.getInstructions());	}

	///////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Convert an OFInstructionApplyActions to string form. The string will be formatted
	 * in a dpctl/ofctl-style syntax.
	 * @param inst; The instruction to convert to a string
	 * @return
	 */
	public static String applyActionsToString(OFInstructionApplyActions inst) throws Exception {
		return ActionUtils.actionsToString(inst.getActions());
	}

	/**
	 * Convert the string representation of an OFInstructionApplyActions to
	 * an OFInstructionApplyActions. The instruction will be set within the
	 * OFFlowMod.Builder provided. Notice nothing is returned, but the
	 * side effect is the addition of an instruction in the OFFlowMod.Builder.
	 * @param fmb; The FMB in which to append the new instruction
	 * @param instStr; The string to parse the instruction from
	 */
	public static void applyActionsFromString(OFFlowMod.Builder fmb, String inst) {

		if (fmb.getVersion().compareTo(OFVersion.OF_11) < 0) {
			log.error("Apply Actions Instruction not supported in OpenFlow 1.0");
			return;
		}

		OFFlowMod.Builder tmpFmb = OFFactories.getFactory(fmb.getVersion()).buildFlowModify();
		OFInstructionApplyActions.Builder ib = OFFactories.getFactory(fmb.getVersion()).instructions().buildApplyActions();
		ActionUtils.fromString(tmpFmb, inst);
		ib.setActions(tmpFmb.getActions());
		log.debug("Appending ApplyActions instruction: {}", ib.build());
		appendInstruction(fmb, ib.build());
		log.debug("All instructions after append: {}", fmb.getInstructions());	}

	///////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Convert an OFInstructionClearActions to string form. The string will be formatted
	 * in a dpctl/ofctl-style syntax.
	 * @param inst; The instruction to convert to a string
	 * @return
	 */
	public static String clearActionsToString(OFInstructionClearActions inst) {
		return ""; // No data for this instruction. The presence of it's key indicates it is to be applied.
	}

	/**
	 * Convert the string representation of an OFInstructionClearActions to
	 * an OFInstructionClearActions. The instruction will be set within the
	 * OFFlowMod.Builder provided. Notice nothing is returned, but the
	 * side effect is the addition of an instruction in the OFFlowMod.Builder.
	 * @param fmb; The FMB in which to append the new instruction
	 * @param instStr; The string to parse the instruction from
	 */
	public static void clearActionsFromString(OFFlowMod.Builder fmb, String inst) {

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
	 * @return
	 */
	public static String meterToString(OFInstructionMeter inst) {
		return Long.toString(inst.getMeterId());
	}

	/**
	 * Convert the string representation of an OFInstructionMeter to
	 * an OFInstructionMeter. The instruction will be set within the
	 * OFFlowMod.Builder provided. Notice nothing is returned, but the
	 * side effect is the addition of an instruction in the OFFlowMod.Builder.
	 * @param fmb; The FMB in which to append the new instruction
	 * @param instStr; The string to parse the instruction from
	 */
	public static void meterFromString(OFFlowMod.Builder fmb, String inst) {
		if (inst == null || inst.isEmpty()) {
			return;
		}

		if (fmb.getVersion().compareTo(OFVersion.OF_13) < 0) {
			log.error("Goto Meter Instruction not supported in OpenFlow 1.0, 1.1, or 1.2");
			return;
		}

		OFInstructionMeter.Builder ib = OFFactories.getFactory(fmb.getVersion()).instructions().buildMeter();

		ib.setMeterId(ParseUtils.parseHexOrDecLong(inst));		

		log.debug("Appending (Goto)Meter instruction: {}", ib.build());
		appendInstruction(fmb, ib.build());
		log.debug("All instructions after append: {}", fmb.getInstructions());
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Convert an OFInstructionExperimenter to string form. The string will be formatted
	 * in a dpctl/ofctl-style syntax.
	 * @param inst; The instruction to convert to a string
	 * @return
	 */
	public static String experimenterToString(OFInstructionExperimenter inst) {
		return Long.toString(inst.getExperimenter());
	}

	/**
	 * Convert the string representation of an OFInstructionExperimenter to
	 * an OFInstructionExperimenter. The instruction will be set within the
	 * OFFlowMod.Builder provided. Notice nothing is returned, but the
	 * side effect is the addition of an instruction in the OFFlowMod.Builder.
	 * @param fmb; The FMB in which to append the new instruction
	 * @param instStr; The string to parse the instruction from
	 */
	public static void experimenterFromString(OFFlowMod.Builder fmb, String inst) {
		log.warn("OFInstructionExperimenter from-string not implemented");
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Convert an deprecated (OF1.5+) OFInstructionMeter to string form.
	 * @param inst; The instruction to convert to a string
	 * @return
	 */
	public static String deprecatedToString(OFInstruction inst) {
		log.warn("OFInstructionDeprecated is being used. Did you mean to use OF1.5+ OFActionMeter?");
		return "";
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Convert JSON string OFInstructionStatTrigger object.
	 * @param fmb; Where the instruction will be placed
	 * @param json; The string to convert to an OFInstructionStatTrigger
	 * @return
	 */
	public static boolean statTriggerFromJsonString(OFFlowMod.Builder fmb, String json) {

		OFInstructionStatTrigger i = statTriggerFromJsonString(fmb.getVersion(), json);
		if (i != null) {
			appendInstruction(fmb, i);
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Convert JSON string OFInstructionStatTrigger object.
	 * @param json; The string to convert to an OFInstructionStatTrigger
	 * @return
	 */
	public static OFInstructionStatTrigger statTriggerFromJsonString(OFVersion v, String json) {
		if (json == null) {
			throw new IllegalArgumentException("JSON string cannot be null");
		}

		final Set<OFStatTriggerFlags> flags = new HashSet<OFStatTriggerFlags>();
		final Set<OFOxs<?>> thresholds = new HashSet<OFOxs<?>>();
		final JsonParser jp;
		try {
			jp = jsonFactory.createParser(json);
		} catch (IOException e) {
			log.error("Could not create JSON parser for OFFlowMod.Builder {}", json);
			return null;
		}
		try {
			if (jp.nextToken() != JsonToken.START_OBJECT) {
				throw new IOException("Expected START_OBJECT");
			}

			while (jp.nextToken() != JsonToken.END_OBJECT) {
				String key = jp.getCurrentName().toLowerCase().trim();
				if (jp.nextToken() != JsonToken.START_ARRAY) {
					throw new IOException("Expected START_ARRAY");
				}
				switch (key) {
				case "flags":
					while (jp.nextToken() != JsonToken.END_ARRAY) {
						boolean flagSet = false;
						for (OFStatTriggerFlags f : OFStatTriggerFlags.values()) {
							if (f.toString().equalsIgnoreCase(jp.getText().trim())) { /* case-insensitive, unlike enum.valueOf() */
								flags.add(f);
								flagSet = true;
								break;
							}
						}
						if (!flagSet) {
							log.warn("Unknown flag {}", jp.getText());
						}
					}
					break;
				case "thresholds":
					while (jp.nextToken() != JsonToken.END_ARRAY) {
						if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
							throw new IOException("Expected START_OBJECT");
						}
						OFOxs.Builder<?> threshold = null;
						U64 value = null;
						while (jp.nextToken() != JsonToken.END_OBJECT) {
							switch (jp.getCurrentName().toLowerCase().trim()) {
							case "oxs_type":
								String type = jp.getText().toLowerCase().trim();
								if (type.equals(StatField.BYTE_COUNT.getName())) {
									threshold = OFFactories.getFactory(v).oxss().buildByteCount();
								} else if (type.equals(StatField.DURATION.getName())) {
									threshold = OFFactories.getFactory(v).oxss().buildDuration();
								} else if (type.equals(StatField.FLOW_COUNT.getName())) {
									threshold = OFFactories.getFactory(v).oxss().buildFlowCount();
								} else if (type.equals(StatField.IDLE_TIME.getName())) {
									threshold = OFFactories.getFactory(v).oxss().buildIdleTime();
								} else if (type.equals(StatField.PACKET_COUNT.getName())) {
									threshold = OFFactories.getFactory(v).oxss().buildPacketCount();
								} else {
									log.warn("Unexpected OXS threshold type {}", type);
								}
								break;
							case "value":
								value = U64.of(ParseUtils.parseHexOrDecLong(jp.getText()));
								break;
							default:
								log.warn("Unexpected OXS threshold key {}", jp.getCurrentName());
								break;
							}
						}
						if (threshold == null || value == null) {
							log.error("Must specify both OXS type and threshold value. Got {} and {}, respectively", threshold, value);
							return null;
						} else {
							if (threshold.getStatField().getName().equals(StatField.BYTE_COUNT.getName())) {
								thresholds.add(((OFOxsByteCount.Builder) threshold).setValue(value).build());
							} else if (threshold.getStatField().getName().equals(StatField.DURATION.getName())) {
								thresholds.add(((OFOxsDuration.Builder) threshold).setValue(value).build());
							} else if (threshold.getStatField().getName().equals(StatField.FLOW_COUNT.getName())) {
								thresholds.add(((OFOxsFlowCount.Builder) threshold).setValue(U32.of(value.getValue())).build());
							} else if (threshold.getStatField().getName().equals(StatField.IDLE_TIME.getName())) {
								thresholds.add(((OFOxsIdleTime.Builder) threshold).setValue(value).build());
							} else if (threshold.getStatField().getName().equals(StatField.PACKET_COUNT.getName())) {
								thresholds.add(((OFOxsPacketCount.Builder) threshold).setValue(value).build());
							} else {
								log.warn("Unexpected OXS threshold type {}", threshold.getStatField().getName());
							}
						}
					}
					break;
				default:
					log.warn("Unexpected OFInstructionStatTrigger key {}", key);
					break;
				}
			}
		} catch (IOException e) {
			log.error("Could not parse: {}", json);
			log.error("JSON parse error message: {}", e.getMessage());
			return null;
		}
		return OFFactories.getFactory(v).instructions().statTrigger(flags, OFOxsList.ofList(thresholds));
	}

	/**
	 * Append OFInstructionStatsTrigger object to an existing JsonGenerator.
	 * This method assumes the field name of the instruction has been
	 * written already, if required. The appended data will
	 * be formatted as follows:
	 *   {
	 *     "flags":[
	 *       f1, f2, f3, ..., fn
	 *     ],
	 *     "thresholds":[
	 *       {
	 *       	"oxs_type":"l",
	 *       	"value":"v"
	 *       },
	 *       {
	 *       	"oxs_type":"m",
	 *       	"value":"v"
	 *       },
	 *       ...,
	 *       {
	 *       	"oxs_type":"n",
	 *       	"value":"v"
	 *       }
	 *     ]
	 *   }
	 * @param jsonGen
	 * @param s
	 */
	public static void statTriggerToJsonString(JsonGenerator jsonGen, OFInstructionStatTrigger s) {
		jsonGen.configure(Feature.WRITE_NUMBERS_AS_STRINGS, true);

		try {
			jsonGen.writeStartObject();
			jsonGen.writeArrayFieldStart("flags");
			for (OFStatTriggerFlags f : s.getFlags()) {
				jsonGen.writeString(f.toString());
			}
			jsonGen.writeEndArray();
			jsonGen.writeArrayFieldStart("thresholds");
			for (OFOxs<?> o : s.getThresholds()) {
				jsonGen.writeStartObject();
				if (o instanceof OFOxsDuration) {
					OFOxsDuration t = (OFOxsDuration) o; 
					jsonGen.writeStringField("oxs_type", t.getStatField().getName());
					jsonGen.writeStringField("value", t.getValue().toString());
				} else if (o instanceof OFOxsByteCount) {
					OFOxsByteCount t = (OFOxsByteCount) o;
					jsonGen.writeStringField("oxs_type", t.getStatField().getName());
					jsonGen.writeStringField("value", t.getValue().toString());
				} else if (o instanceof OFOxsFlowCount) {
					OFOxsFlowCount t = (OFOxsFlowCount) o;
					jsonGen.writeStringField("oxs_type", t.getStatField().getName());
					jsonGen.writeStringField("value", t.getValue().toString());
				} else if (o instanceof OFOxsIdleTime) {
					OFOxsIdleTime t = (OFOxsIdleTime) o;
					jsonGen.writeStringField("oxs_type", t.getStatField().getName());
					jsonGen.writeStringField("value", t.getValue().toString());
				} else if (o instanceof OFOxsPacketCount) {
					OFOxsPacketCount t = (OFOxsPacketCount) o;
					jsonGen.writeStringField("oxs_type", t.getStatField().getName());
					jsonGen.writeStringField("value", t.getValue().toString());
				} else {
					log.warn("Skipping unknown OXS type {}", o.getStatField().getName());
				}
				jsonGen.writeEndObject();
			}
			jsonGen.writeEndArray();
			jsonGen.close();
		} catch (IOException e) {
			log.error("Error composing OFInstructionStatTrigger JSON object. {}", e.getMessage());
		}
	}

	/**
	 * Create OFInstructionStatsTrigger JSON object string.
	 * This method assumes the field name of the instruction will be
	 * written externally, if required. The appended JSON string will
	 * be formatted as follows:
	 *   {
	 *     "flags":[
	 *       f1, f2, f3, ..., fn
	 *     ],
	 *     "thresholds":[
	 *       {
	 *       	"oxs_type":"l",
	 *       	"value":"v"
	 *       },
	 *       {
	 *       	"oxs_type":"m",
	 *       	"value":"v"
	 *       },
	 *       ...,
	 *       {
	 *       	"oxs_type":"n",
	 *       	"value":"v"
	 *       }
	 *     ]
	 *   }
	 * @param s
	 */
	public static String statTriggerToJsonString(OFInstructionStatTrigger s) {
		Writer w = new StringWriter();
		JsonGenerator jsonGen;
		try {
			jsonGen = jsonFactory.createGenerator(w);
		} catch (IOException e) {
			log.error("Could not instantiate JSON Generator. {}", e.getMessage());
			return JSON_EMPTY_OBJECT;
		}

		statTriggerToJsonString(jsonGen, s);

		return w.toString(); /* overridden impl returns contents of Writer's StringBuffer */
	}
}