package net.floodlightcontroller.core.internal;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.annotation.Nonnull;

import net.floodlightcontroller.core.IOFConnectionBackend;
import net.floodlightcontroller.core.IOFSwitchBackend;
import net.floodlightcontroller.core.IOFSwitchDriver;
import net.floodlightcontroller.core.SwitchDescription;

import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

/**
 * This implementation of ISwitchDriverRegistry uses a naive algorithm to
 * perform longest prefix matching on the manufacturer description prefixes
 *
 * We maintain a map that maps prefixes to the drivers as well as a sorted
 * set that contains the prefixes sorted by their length. We exploit the fact
 * that lexicographical order defines that shorter strings are always less
 * than longer strings with the same prefix). Thus we can use reverse order for
 * our purposes.
 * To perform a lookup we iterate through the sorted set until we find a prefix
 * that matches the manufacturer description. Since the set is sorted this
 * will be the longest matching prefix.
 *
 * @author gregor
 */
class NaiveSwitchDriverRegistry implements ISwitchDriverRegistry {

    protected static final Logger log = LoggerFactory.getLogger(NaiveSwitchDriverRegistry.class);
    private final SortedSet<String> switchDescSorted;
    private final Map<String,IOFSwitchDriver> switchBindingMap;
    private final IOFSwitchManager switchManager;

    public NaiveSwitchDriverRegistry(@Nonnull IOFSwitchManager switchManager) {
        Preconditions.checkNotNull(switchManager, "switchManager must not be null");
        this.switchManager  = switchManager;
        switchBindingMap = new HashMap<String, IOFSwitchDriver>();
        switchDescSorted = new TreeSet<String>(Collections.reverseOrder());
    }

    @Override
    public synchronized void addSwitchDriver(@Nonnull String manufacturerDescPrefix,
                                             @Nonnull IOFSwitchDriver driver) {
        Preconditions.checkNotNull(manufacturerDescPrefix, "manufactererDescProfix");
        Preconditions.checkNotNull(driver, "driver");

        IOFSwitchDriver existingDriver = switchBindingMap.get(manufacturerDescPrefix);
        if (existingDriver != null ) {
            throw new IllegalStateException("Failed to add OFSwitch driver for "
                    + manufacturerDescPrefix + "already registered");
        }
        switchBindingMap.put(manufacturerDescPrefix, driver);
        switchDescSorted.add(manufacturerDescPrefix);
    }

    @Override
    // TODO: instead of synchronized we could actually use a r/w lock
    // but it's probably not worth it.
    public synchronized IOFSwitchBackend
            getOFSwitchInstance(@Nonnull IOFConnectionBackend connection, @Nonnull SwitchDescription description,
                    @Nonnull OFFactory factory, @Nonnull DatapathId id) {
        Preconditions.checkNotNull(connection, "connection");
        Preconditions.checkNotNull(description, "description");
        Preconditions.checkNotNull(factory, "factory");
        Preconditions.checkNotNull(id, "id");

        Preconditions.checkNotNull(description.getHardwareDescription(), "hardware description");
        Preconditions.checkNotNull(description.getManufacturerDescription(), "manufacturer description");
        Preconditions.checkNotNull(description.getSerialNumber(), "serial number");
        Preconditions.checkNotNull(description.getDatapathDescription(), "datapath description");
        Preconditions.checkNotNull(description.getSoftwareDescription(), "software description");

        // Find the appropriate driver
        for (String descPrefix: switchDescSorted) {
            if (description.getManufacturerDescription()
                    .startsWith(descPrefix)) {
                IOFSwitchDriver driver = switchBindingMap.get(descPrefix);
                IOFSwitchBackend sw = driver.getOFSwitchImpl(description, factory);
                if (sw != null) {
                    sw.setSwitchProperties(description);
                    return sw;
                }
            }
        }
        // no switch found
        IOFSwitchBackend sw = new OFSwitch(connection, factory, switchManager, id);
        sw.setSwitchProperties(description);
        return sw;
    }

}
