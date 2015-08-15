package net.floodlightcontroller.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFeaturesReply;
import org.projectfloodlight.openflow.protocol.OFPortDesc;

import net.floodlightcontroller.core.SwitchDescription;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.util.HexString;
import org.projectfloodlight.openflow.protocol.OFCapabilities;
import org.projectfloodlight.openflow.protocol.OFActionType;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a switch in the BigSync store. It works out nicely that we
 * just need to store the FeaturesReply and the DescriptionStatistics in the
 * store.
 * @author gregor
 *
 */
public class SwitchSyncRepresentation {
    public static class SyncedPort {
        @JsonProperty
        public OFPortDesc port;

        /*public static SyncedPort fromImmutablePort(OFPortDesc p) {
            SyncedPort rv = new SyncedPort();
            rv.port = OFPortDesc.of(p.getPortNumber());
            if (p.getHardwareAddress() == null) {
                rv.hardwareAddress = 0;
            } else {
                rv.hardwareAddress =
                        MACAddress.valueOf(p.getHardwareAddress()).toLong();
            }
            rv.name = p.getName();
            rv.config = EnumBitmaps.toBitmap(p.getConfig());
            rv.state = p.getStpState().getValue();
            if (p.isLinkDown())
                rv.state |= OFPortState.OFPPS_LINK_DOWN.getValue();
            rv.currentFeatures  = EnumBitmaps.toBitmap(p.getCurrentFeatures());
            rv.advertisedFeatures =
                    EnumBitmaps.toBitmap(p.getAdvertisedFeatures());
            rv.supportedFeatures =
                    EnumBitmaps.toBitmap(p.getSupportedFeatures());
            rv.peerFeatures = EnumBitmaps.toBitmap(p.getPeerFeatures());
            return rv;
        }*/

        public static SyncedPort fromOFPortDesc(OFPortDesc ofpd) {
	            SyncedPort sp = new SyncedPort();
	            sp.port = ofpd;
	            return sp;
        } 
        
        public OFPortDesc toOFPortDesc(OFFactory factory) {
        	OFPortDesc.Builder builder = factory.buildPortDesc();
            builder.setPortNo(port.getPortNo());
            builder.setHwAddr(port.getHwAddr());
            builder.setName(port.getName());
            builder.setConfig(port.getConfig());
            builder.setState(port.getState());
            builder.setCurr(port.getCurr());
            builder.setAdvertised(port.getAdvertised());
            builder.setSupported(port.getSupported());
            builder.setPeer(port.getPeer());
            return builder.build();
        }
    }

    // From FeaturesReply
    private final DatapathId dpid;
    private final long buffers;
    private final short tables;
    private final Set<OFCapabilities> capabilities;
    private final Set<OFActionType> actions;
    private final List<SyncedPort> ports;

    // From OFDescStatsReply
    private final String manufacturerDescription;
    private final String hardwareDescription;
    private final String softwareDescription;
    private final String serialNumber;
    private final String datapathDescription;



    /**
     * @param dpid
     * @param buffers
     * @param tables
     * @param capabilities
     * @param actions
     * @param ports
     * @param manufacturerDescription
     * @param hardwareDescription
     * @param softwareDescription
     * @param serialNumber
     * @param datapathDescription
     */
    @JsonCreator
    public SwitchSyncRepresentation(
            @JsonProperty("dpid") DatapathId dpid,
            @JsonProperty("buffers") int buffers,
            @JsonProperty("tables") byte tables,
            @JsonProperty("capabilities") Set<OFCapabilities> capabilities,
            @JsonProperty("actions") Set<OFActionType> actions,
            @JsonProperty("ports") List<SyncedPort> ports,
            @JsonProperty("manufacturerDescription") String manufacturerDescription,
            @JsonProperty("hardwareDescription") String hardwareDescription,
            @JsonProperty("softwareDescription") String softwareDescription,
            @JsonProperty("serialNumber") String serialNumber,
            @JsonProperty("datapathDescription") String datapathDescription) {
        this.dpid = dpid;
        this.buffers = buffers;
        this.tables = tables;
        this.capabilities = capabilities;
        this.actions = actions;
        this.ports = ports;
        this.manufacturerDescription = manufacturerDescription;
        this.hardwareDescription = hardwareDescription;
        this.softwareDescription = softwareDescription;
        this.serialNumber = serialNumber;
        this.datapathDescription = datapathDescription;
    }

    public SwitchSyncRepresentation(IOFSwitch sw) {
        this.dpid = sw.getId();
        this.buffers = sw.getBuffers();
        this.tables = (short) sw.getNumTables();
        this.capabilities = sw.getCapabilities();
        this.actions = sw.getActions();
        this.ports = toSyncedPortList(sw.getPorts());

        SwitchDescription d = sw.getSwitchDescription();
        this.manufacturerDescription = d.getManufacturerDescription();
        this.hardwareDescription = d.getHardwareDescription();
        this.softwareDescription = d.getSoftwareDescription();
        this.serialNumber = d.getSerialNumber();
        this.datapathDescription = d.getDatapathDescription();
    }

    public SwitchSyncRepresentation(OFFeaturesReply fr,
                                    SwitchDescription d) {
        this.dpid = fr.getDatapathId();
        this.buffers = fr.getNBuffers();
        this.tables = fr.getNTables();
        this.capabilities = fr.getCapabilities();
        this.actions = fr.getActions();
        this.ports = toSyncedPortList(fr.getPorts());

        this.manufacturerDescription = d.getManufacturerDescription();
        this.hardwareDescription = d.getHardwareDescription();
        this.softwareDescription = d.getSoftwareDescription();
        this.serialNumber = d.getSerialNumber();
        this.datapathDescription = d.getDatapathDescription();
    }

    private static List<SyncedPort> toSyncedPortList(Collection<OFPortDesc> ports) {
        List<SyncedPort> rv = new ArrayList<SyncedPort>(ports.size());
        for (OFPortDesc p: ports) {
            rv.add(SyncedPort.fromOFPortDesc(p));
        }
        return rv;
    }

    private static List<OFPortDesc> toOFPortDescList(OFFactory factory, Collection<SyncedPort> ports) {
        List<OFPortDesc> rv = new ArrayList<OFPortDesc>(ports.size());
        for (SyncedPort p: ports) {
            rv.add(p.toOFPortDesc(factory));
        }
        return rv;

    }

    @JsonIgnore
    public OFFeaturesReply getFeaturesReply(OFFactory factory) {
    	/**
         * FIXME Icky work around; if a null actions got written to storage
         * then fake up an empty one so the builder() doesn't throw
         * a NPE.  Need to root cause why someone would write a null actions.
         * This code will all be removed shortly -- needed to unblock BVS team.
         */
        Set<OFActionType> workAroundActions;
        if (actions != null)
            workAroundActions = actions;
        else
            workAroundActions = Collections.<OFActionType> emptySet();

        OFFeaturesReply featuresReply = factory.buildFeaturesReply()
                .setXid(0)
                .setDatapathId(dpid)
                .setNBuffers(buffers)
                .setNTables(tables)
                .setCapabilities(capabilities)
                .setActions(workAroundActions)
                .setPorts(toOFPortDescList(factory, ports))
                .build();
        return featuresReply;
    }

    @JsonIgnore
    public SwitchDescription getDescription() {
    	return new SwitchDescription(manufacturerDescription,
                hardwareDescription, softwareDescription, softwareDescription,
                datapathDescription);
    }

    public DatapathId getDpid() {
        return dpid;
    }

    public long getBuffers() {
        return buffers;
    }

    public short getTables() {
        return tables;
    }

    public Set<OFCapabilities> getCapabilities() {
        return capabilities;
    }

    public Set<OFActionType> getActions() {
        return actions;
    }

    public List<SyncedPort> getPorts() {
        return ports;
    }

    public String getManufacturerDescription() {
        return manufacturerDescription;
    }

    public String getHardwareDescription() {
        return hardwareDescription;
    }

    public String getSoftwareDescription() {
        return softwareDescription;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getDatapathDescription() {
        return datapathDescription;
    }

    @Override
    public String toString() {
        String dpidString;
        dpidString = HexString.toHexString(dpid.getLong());
        return "SwitchSyncRepresentation [DPID=" + dpidString + "]";
    }
}
