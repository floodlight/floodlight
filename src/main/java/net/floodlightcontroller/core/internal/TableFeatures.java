package net.floodlightcontroller.core.internal;

import java.util.List;

import org.projectfloodlight.openflow.protocol.OFTableFeatureProp;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropApplyActions;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropApplyActionsMiss;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropApplySetfield;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropApplySetfieldMiss;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropExperimenter;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropExperimenterMiss;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropInstructions;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropInstructionsMiss;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropMatch;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropNextTables;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropNextTablesMiss;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropTableSyncFrom;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropType;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropWildcards;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropWriteActions;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropWriteActionsMiss;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropWriteSetfield;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropWriteSetfieldMiss;
import org.projectfloodlight.openflow.protocol.OFTableFeatures;
import org.projectfloodlight.openflow.protocol.ver13.OFTableFeaturePropTypeSerializerVer13;
import org.projectfloodlight.openflow.protocol.ver14.OFTableFeaturePropTypeSerializerVer14;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;

/**
 * A small, immutable container for organizing the features of a
 * particular table on a switch. OFTableFeatures is pretty much the 
 * same, but it doesn't easily expose the properties for quick access.
 * 
 * This class will sacrifice the added time to instantiate instead
 * of during each access to a particular property. Had the properties
 * been in a Map of some sort, this might not be necessary. The
 * assumption is that we won't ever change switch table properties
 * frequently. It's more of a setup/initialization task. Otherwise,
 * frequently making these would be quite inefficient considering
 * the amount of data within and the number of tables switches can have.
 * 
 * <b> FIXME: OFTableFeatureProp types don't override equals in Loxi... </b>
 * 
 * @author Ryan Izard, rizard@g.clemson.edu, ryan.izard@bigswitch.com
 *
 */
public class TableFeatures {
	/* OF1.3+ */
	private OFTableFeaturePropApplyActions aa;
	private OFTableFeaturePropApplyActionsMiss aam;
	private OFTableFeaturePropApplySetfield asf;
	private OFTableFeaturePropApplySetfieldMiss asfm;
	private OFTableFeaturePropExperimenter e;
	private OFTableFeaturePropExperimenterMiss em;
	private OFTableFeaturePropInstructions i;
	private OFTableFeaturePropInstructionsMiss im;
	private OFTableFeaturePropMatch m;
	private OFTableFeaturePropNextTables nt;
	private OFTableFeaturePropNextTablesMiss ntm;
	private OFTableFeaturePropWildcards w;
	private OFTableFeaturePropWriteActions wa;
	private OFTableFeaturePropWriteActionsMiss wam;
	private OFTableFeaturePropWriteSetfield wsf;
	private OFTableFeaturePropWriteSetfieldMiss wsfm;
	/* OF1.4+ */
	private OFTableFeaturePropTableSyncFrom tsf;
	
	private long config;
	private long maxEntries;
	private U64 metadataMatch;
	private U64 metadataWrite;
	private String tableName;
	private TableId tableId;
	
	/*
	 * Loxi-style constructor for familiarity. Also eliminates
	 * the need to use the new keyword.
	 */
	public static TableFeatures of(OFTableFeatures tableFeatures) {
		return new TableFeatures(tableFeatures);
	}
	
	/*
	 * Make default constructor inaccessible. We don't set default
	 * values so, we must start with an OFTableFeatures.
	 */
	private TableFeatures() {}
	
	/*
	 * Called from of(OFTableFeatures tableFeatures) above.
	 * Private to avoid confusion.
	 */
	private TableFeatures(OFTableFeatures tf) {
		/*
		 * First, get the properties.
		 */
		List<OFTableFeatureProp> properties = tf.getProperties();
		for (OFTableFeatureProp p : properties) {
			OFTableFeaturePropType pt = getTableFeaturePropType(p);
			switch (pt) {
			case APPLY_ACTIONS:
				aa = (OFTableFeaturePropApplyActions) p;
				break;
			case APPLY_ACTIONS_MISS:
				aam = (OFTableFeaturePropApplyActionsMiss) p;
				break;
			case APPLY_SETFIELD:
				asf = (OFTableFeaturePropApplySetfield) p;
				break;
			case APPLY_SETFIELD_MISS:
				asfm = (OFTableFeaturePropApplySetfieldMiss) p;
				break;
			case EXPERIMENTER:
				e = (OFTableFeaturePropExperimenter) p;
				break;
			case EXPERIMENTER_MISS:
				em = (OFTableFeaturePropExperimenterMiss) p;
				break;
			case INSTRUCTIONS:
				i = (OFTableFeaturePropInstructions) p;
				break;
			case INSTRUCTIONS_MISS:
				im = (OFTableFeaturePropInstructionsMiss) p;
				break;
			case MATCH:
				m = (OFTableFeaturePropMatch) p;
				break;
			case NEXT_TABLES:
				nt = (OFTableFeaturePropNextTables) p;
				break;
			case NEXT_TABLES_MISS:
				ntm = (OFTableFeaturePropNextTablesMiss) p;
				break;
			case TABLE_SYNC_FROM:
				tsf = (OFTableFeaturePropTableSyncFrom) p;
				break;
			case WILDCARDS:
				w = (OFTableFeaturePropWildcards) p;
				break;
			case WRITE_ACTIONS:
				wa = (OFTableFeaturePropWriteActions) p;
				break;
			case WRITE_ACTIONS_MISS:
				wam = (OFTableFeaturePropWriteActionsMiss) p;
				break;
			case WRITE_SETFIELD:
				wsf = (OFTableFeaturePropWriteSetfield) p;
				break;
			case WRITE_SETFIELD_MISS:
				wsfm = (OFTableFeaturePropWriteSetfieldMiss) p;
				break;
			default:
				throw new UnsupportedOperationException("OFTableFeaturePropType " + pt.toString() + " not accounted for in " + this.getClass().getCanonicalName());
			}
		}
		
		/*
		 * Next, get the other info e.g. name, ID, etc.
		 */
		config = tf.getConfig();
		maxEntries = tf.getMaxEntries();
		metadataMatch = tf.getMetadataMatch();
		metadataWrite = tf.getMetadataWrite();
		tableId = tf.getTableId();
		tableName = tf.getName();
	}
	
	/*
	 * Internal helper function to deserialize the property type in a version-agnostic way
	 * from the perspective of the calling function.
	 */
	private static OFTableFeaturePropType getTableFeaturePropType(OFTableFeatureProp p) {
		switch (p.getVersion()) {
		case OF_13:
			return OFTableFeaturePropTypeSerializerVer13.ofWireValue((short) p.getType());
		case OF_14:
			return OFTableFeaturePropTypeSerializerVer14.ofWireValue((short) p.getType());
		default:
			throw new IllegalArgumentException("OFVersion " + p.getVersion().toString() + " does not support OFTableFeature messages.");
		}
	}
	
	public long getConfig() {
		return config;
	}
	
	public long getMaxEntries() {
		return maxEntries;
	}
	
	public U64 getMetadataMatch() {
		return metadataMatch;
	}
	
	public U64 getMetadataWrite() {
		return metadataWrite;
	}
	
	public String getTableName() {
		return tableName;
	}
	
	public TableId getTableId() {
		return tableId;
	}
	
	public OFTableFeaturePropApplyActions getPropApplyActions() {
		return aa;
	}
	
	public OFTableFeaturePropApplyActionsMiss getPropApplyActionsMiss() {
		return aam;
	}
	
	public OFTableFeaturePropApplySetfield getPropApplySetField() {
		return asf;
	}
	
	public OFTableFeaturePropApplySetfieldMiss getPropApplySetFieldMiss() {
		return asfm;
	}
	
	public OFTableFeaturePropExperimenter getPropExperimenter() {
		return e;
	}
	
	public OFTableFeaturePropExperimenterMiss getPropExperimenterMiss() {
		return em;
	}
	
	public OFTableFeaturePropInstructions getPropInstructions() {
		return i;
	}
	
	public OFTableFeaturePropInstructionsMiss getPropInstructionsMiss() {
		return im;
	}
	
	public OFTableFeaturePropMatch getPropMatch() {
		return m;
	}

	public OFTableFeaturePropNextTables getPropNextTables() {
		return nt;
	}
	
	public OFTableFeaturePropNextTablesMiss getPropNextTablesMiss() {
		return ntm;
	}
	
	public OFTableFeaturePropWildcards getPropWildcards() {
		return w;
	}
	
	public OFTableFeaturePropWriteActions getPropWriteActions() {
		return wa;
	}
	
	public OFTableFeaturePropWriteActionsMiss getPropWriteActionsMiss() {
		return wam;
	}
	
	public OFTableFeaturePropWriteSetfield getPropWriteSetField() {
		return wsf;
	}
	
	public OFTableFeaturePropWriteSetfieldMiss getPropWriteSetFieldMiss() {
		return wsfm;
	}
	
	public OFTableFeaturePropTableSyncFrom getPropTableSyncFrom() {
		return tsf;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((aa == null) ? 0 : aa.hashCode());
		result = prime * result + ((aam == null) ? 0 : aam.hashCode());
		result = prime * result + ((asf == null) ? 0 : asf.hashCode());
		result = prime * result + ((asfm == null) ? 0 : asfm.hashCode());
		result = prime * result + (int) (config ^ (config >>> 32));
		result = prime * result + ((e == null) ? 0 : e.hashCode());
		result = prime * result + ((em == null) ? 0 : em.hashCode());
		result = prime * result + ((i == null) ? 0 : i.hashCode());
		result = prime * result + ((im == null) ? 0 : im.hashCode());
		result = prime * result + ((m == null) ? 0 : m.hashCode());
		result = prime * result + (int) (maxEntries ^ (maxEntries >>> 32));
		result = prime * result
				+ ((metadataMatch == null) ? 0 : metadataMatch.hashCode());
		result = prime * result
				+ ((metadataWrite == null) ? 0 : metadataWrite.hashCode());
		result = prime * result + ((nt == null) ? 0 : nt.hashCode());
		result = prime * result + ((ntm == null) ? 0 : ntm.hashCode());
		result = prime * result + ((tableId == null) ? 0 : tableId.hashCode());
		result = prime * result
				+ ((tableName == null) ? 0 : tableName.hashCode());
		result = prime * result + ((tsf == null) ? 0 : tsf.hashCode());
		result = prime * result + ((w == null) ? 0 : w.hashCode());
		result = prime * result + ((wa == null) ? 0 : wa.hashCode());
		result = prime * result + ((wam == null) ? 0 : wam.hashCode());
		result = prime * result + ((wsf == null) ? 0 : wsf.hashCode());
		result = prime * result + ((wsfm == null) ? 0 : wsfm.hashCode());
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
		TableFeatures other = (TableFeatures) obj;
		if (aa == null) {
			if (other.aa != null)
				return false;
		} else if (!aa.equals(other.aa))
			return false;
		if (aam == null) {
			if (other.aam != null)
				return false;
		} else if (!aam.equals(other.aam))
			return false;
		if (asf == null) {
			if (other.asf != null)
				return false;
		} else if (!asf.equals(other.asf))
			return false;
		if (asfm == null) {
			if (other.asfm != null)
				return false;
		} else if (!asfm.equals(other.asfm))
			return false;
		if (config != other.config)
			return false;
		if (e == null) {
			if (other.e != null)
				return false;
		} else if (!e.equals(other.e))
			return false;
		if (em == null) {
			if (other.em != null)
				return false;
		} else if (!em.equals(other.em))
			return false;
		if (i == null) {
			if (other.i != null)
				return false;
		} else if (!i.equals(other.i))
			return false;
		if (im == null) {
			if (other.im != null)
				return false;
		} else if (!im.equals(other.im))
			return false;
		if (m == null) {
			if (other.m != null)
				return false;
		} else if (!m.equals(other.m))
			return false;
		if (maxEntries != other.maxEntries)
			return false;
		if (metadataMatch == null) {
			if (other.metadataMatch != null)
				return false;
		} else if (!metadataMatch.equals(other.metadataMatch))
			return false;
		if (metadataWrite == null) {
			if (other.metadataWrite != null)
				return false;
		} else if (!metadataWrite.equals(other.metadataWrite))
			return false;
		if (nt == null) {
			if (other.nt != null)
				return false;
		} else if (!nt.equals(other.nt))
			return false;
		if (ntm == null) {
			if (other.ntm != null)
				return false;
		} else if (!ntm.equals(other.ntm))
			return false;
		if (tableId == null) {
			if (other.tableId != null)
				return false;
		} else if (!tableId.equals(other.tableId))
			return false;
		if (tableName == null) {
			if (other.tableName != null)
				return false;
		} else if (!tableName.equals(other.tableName))
			return false;
		if (tsf == null) {
			if (other.tsf != null)
				return false;
		} else if (!tsf.equals(other.tsf))
			return false;
		if (w == null) {
			if (other.w != null)
				return false;
		} else if (!w.equals(other.w))
			return false;
		if (wa == null) {
			if (other.wa != null)
				return false;
		} else if (!wa.equals(other.wa))
			return false;
		if (wam == null) {
			if (other.wam != null)
				return false;
		} else if (!wam.equals(other.wam))
			return false;
		if (wsf == null) {
			if (other.wsf != null)
				return false;
		} else if (!wsf.equals(other.wsf))
			return false;
		if (wsfm == null) {
			if (other.wsfm != null)
				return false;
		} else if (!wsfm.equals(other.wsfm))
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return "TableFeatures [TableName=" + tableName + ", TableId=" + tableId
				+ ", Config=" + config + ", MaxEntries=" + maxEntries 
				+ ", MetadataMatch=" + metadataMatch + ", MetadataWrite=" + metadataWrite
				+ ", ApplyActions=" + aa + ", ApplyActionsMiss=" + aam + ", ApplySetField=" + asf
				+ ", ApplySetFieldMiss=" + asfm + ", Experimenter=" + e + ", ExperimenterMiss=" + em + ", Instructions=" + i
				+ ", InstructionsMiss=" + im + ", Match=" + m + ", NextTable=" + nt + ", NextTableMiss=" + ntm
				+ ", Wildcards=" + w + ", WriteActions=" + wa + ", WriteActionsMiss=" + wam + ", WriteSetField=" + wsf
				+ ", WriteSetFieldMiss=" + wsfm + ", TableSyncFrom=" + tsf 
				+ "]";
	}
}