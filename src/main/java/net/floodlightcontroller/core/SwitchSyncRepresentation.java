package net.floodlightcontroller.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.floodlightcontroller.util.EnumBitmaps;
import net.floodlightcontroller.util.MACAddress;

import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPhysicalPort.OFPortState;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.util.HexString;

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
        public short portNumber;
        @JsonProperty
        public long hardwareAddress;
        @JsonProperty
        public String name;
        @JsonProperty
        public int config;
        @JsonProperty
        public int state;
        @JsonProperty
        public int currentFeatures;
        @JsonProperty
        public int advertisedFeatures;
        @JsonProperty
        public int supportedFeatures;
        @JsonProperty
        public int peerFeatures;

        public static SyncedPort fromImmutablePort(ImmutablePort p) {
            SyncedPort rv = new SyncedPort();
            rv.portNumber = p.getPortNumber();
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
        }

        public OFPhysicalPort toOFPhysicalPort() {
            OFPhysicalPort p = new OFPhysicalPort();
            p.setPortNumber(portNumber);
            p.setHardwareAddress(MACAddress.valueOf(hardwareAddress).toBytes());
            p.setName(name);
            p.setConfig(config);
            p.setState(state);
            p.setCurrentFeatures(currentFeatures);
            p.setAdvertisedFeatures(advertisedFeatures);
            p.setSupportedFeatures(supportedFeatures);
            p.setPeerFeatures(peerFeatures);
            return p;
        }
    }

    // From FeaturesReply
    private final long dpid;
    private final int buffers;
    private final byte tables;
    private final int capabilities;
    private final int actions;
    private final List<SyncedPort> ports;

    // From OFDescriptionStatistics
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
            @JsonProperty("dpid") long dpid,
            @JsonProperty("buffers") int buffers,
            @JsonProperty("tables") byte tables,
            @JsonProperty("capabilities") int capabilities,
            @JsonProperty("actions") int actions,
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
        this.tables = sw.getTables();
        this.capabilities = sw.getCapabilities();
        this.actions = sw.getActions();
        this.ports = toSyncedPortList(sw.getPorts());

        OFDescriptionStatistics d = sw.getDescriptionStatistics();
        this.manufacturerDescription = d.getManufacturerDescription();
        this.hardwareDescription = d.getHardwareDescription();
        this.softwareDescription = d.getSoftwareDescription();
        this.serialNumber = d.getSerialNumber();
        this.datapathDescription = d.getDatapathDescription();
    }

    public SwitchSyncRepresentation(OFFeaturesReply fr,
                                    OFDescriptionStatistics d) {
        this.dpid = fr.getDatapathId();
        this.buffers = fr.getBuffers();
        this.tables = fr.getTables();
        this.capabilities = fr.getCapabilities();
        this.actions = fr.getActions();
        this.ports = toSyncedPortList(
                ImmutablePort.immutablePortListOf(fr.getPorts()));

        this.manufacturerDescription = d.getManufacturerDescription();
        this.hardwareDescription = d.getHardwareDescription();
        this.softwareDescription = d.getSoftwareDescription();
        this.serialNumber = d.getSerialNumber();
        this.datapathDescription = d.getDatapathDescription();
    }

    private static List<SyncedPort> toSyncedPortList(Collection<ImmutablePort> ports) {
        List<SyncedPort> rv = new ArrayList<SyncedPort>(ports.size());
        for (ImmutablePort p: ports) {
            rv.add(SyncedPort.fromImmutablePort(p));
        }
        return rv;
    }

    private static List<OFPhysicalPort> toOFPhysicalPortList(Collection<SyncedPort> ports) {
        List<OFPhysicalPort> rv = new ArrayList<OFPhysicalPort>(ports.size());
        for (SyncedPort p: ports) {
            rv.add(p.toOFPhysicalPort());
        }
        return rv;

    }

    @JsonIgnore
    public OFFeaturesReply getFeaturesReply() {
        OFFeaturesReply fr = new OFFeaturesReply();
        fr.setDatapathId(dpid);
        fr.setBuffers(buffers);
        fr.setTables(tables);
        fr.setCapabilities(capabilities);
        fr.setActions(actions);
        fr.setPorts(toOFPhysicalPortList(ports));
        return fr;
    }

    @JsonIgnore
    public OFDescriptionStatistics getDescription() {
        OFDescriptionStatistics desc = new OFDescriptionStatistics();
        desc.setManufacturerDescription(manufacturerDescription);
        desc.setHardwareDescription(hardwareDescription);
        desc.setSoftwareDescription(softwareDescription);
        desc.setSerialNumber(serialNumber);
        desc.setDatapathDescription(datapathDescription);
        return desc;
    }



    public long getDpid() {
        return dpid;
    }

    public int getBuffers() {
        return buffers;
    }

    public byte getTables() {
        return tables;
    }

    public int getCapabilities() {
        return capabilities;
    }

    public int getActions() {
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
        dpidString = HexString.toHexString(dpid);
        return "SwitchSyncRepresentation [DPID=" + dpidString + "]";
    }
}
