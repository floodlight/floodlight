package net.floodlightcontroller.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import net.floodlightcontroller.util.EnumBitmaps;

import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPhysicalPort.OFPortConfig;
import org.openflow.protocol.OFPhysicalPort.OFPortFeatures;
import org.openflow.protocol.OFPhysicalPort.OFPortState;


/**
 * An immutable version of an OFPhysical port. In addition, it uses EnumSets
 * instead of integer bitmaps to represent
 * OFPortConfig, OFPortState, and OFPortFeature bitmaps.
 *
 * @author gregor
 *
 */
public class ImmutablePhysicalPort {
    private final short portNumber;
    private final byte[] hardwareAddress;
    private final String name;
    private final EnumSet<OFPortConfig> config;
    private final int state; // OFPortState is a mess
    private final EnumSet<OFPortFeatures> currentFeatures;
    private final EnumSet<OFPortFeatures> advertisedFeatures;
    private final EnumSet<OFPortFeatures> supportedFeatures;
    private final EnumSet<OFPortFeatures> peerFeatures;

    public static ImmutablePhysicalPort fromOFPhysicalPort(OFPhysicalPort p) {
        return new ImmutablePhysicalPort(
                p.getPortNumber(),
                Arrays.copyOf(p.getHardwareAddress(), 6),
                p.getName(),
                EnumBitmaps.toEnumSet(OFPortConfig.class, p.getConfig()),
                p.getState(),
                EnumBitmaps.toEnumSet(OFPortFeatures.class,
                                      p.getCurrentFeatures()),
                EnumBitmaps.toEnumSet(OFPortFeatures.class,
                                      p.getAdvertisedFeatures()),
                EnumBitmaps.toEnumSet(OFPortFeatures.class,
                                      p.getSupportedFeatures()),
                EnumBitmaps.toEnumSet(OFPortFeatures.class,
                                      p.getPeerFeatures())
                                      );
    }

    /**
     * @param portNumber
     * @param hardwareAddress
     * @param name
     * @param config
     * @param state
     * @param currentFeatures
     * @param advertisedFeatures
     * @param supportedFeatures
     * @param peerFeatures
     */
    private ImmutablePhysicalPort(short portNumber, byte[] hardwareAddress,
                                 String name, EnumSet<OFPortConfig> config,
                                 int state,
                                 EnumSet<OFPortFeatures> currentFeatures,
                                 EnumSet<OFPortFeatures> advertisedFeatures,
                                 EnumSet<OFPortFeatures> supportedFeatures,
                                 EnumSet<OFPortFeatures> peerFeatures) {
        this.portNumber = portNumber;
        this.hardwareAddress = hardwareAddress;
        this.name = name;
        this.config = config;
        this.state = state;
        this.currentFeatures = currentFeatures;
        this.advertisedFeatures = advertisedFeatures;
        this.supportedFeatures = supportedFeatures;
        this.peerFeatures = peerFeatures;
    }

    public short getPortNumber() {
        return portNumber;
    }

    public byte[] getHardwareAddress() {
        // FIXME: don't use arrays.
        return Arrays.copyOf(hardwareAddress, 6);
    }

    public String getName() {
        return name;
    }

    public Set<OFPortConfig> getConfig() {
        return Collections.unmodifiableSet(config);
    }

    public int getState() {
        return state;
    }

    public Set<OFPortFeatures> getCurrentFeatures() {
        return Collections.unmodifiableSet(currentFeatures);
    }

    public Set<OFPortFeatures> getAdvertisedFeatures() {
        return Collections.unmodifiableSet(advertisedFeatures);
    }

    public Set<OFPortFeatures> getSupportedFeatures() {
        return Collections.unmodifiableSet(supportedFeatures);
    }

    public Set<OFPortFeatures> getPeerFeatures() {
        return Collections.unmodifiableSet(peerFeatures);
    }
}
