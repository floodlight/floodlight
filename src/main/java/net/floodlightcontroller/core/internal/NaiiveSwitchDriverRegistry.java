package net.floodlightcontroller.core.internal;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.openflow.protocol.statistics.OFDescriptionStatistics;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchDriver;

/**
 * This implementation of ISwitchDriverRegistry uses a naiive algorithm to
 * perform longest prefix matching on the manufacturere description prefixes
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
class NaiiveSwitchDriverRegistry implements ISwitchDriverRegistry {
    private final SortedSet<String> switchDescSorted;
    private final Map<String,IOFSwitchDriver> switchBindingMap;

    public NaiiveSwitchDriverRegistry() {
        switchBindingMap = new HashMap<String, IOFSwitchDriver>();
        switchDescSorted = new TreeSet<String>(Collections.reverseOrder());
    }

    @Override
    public synchronized void addSwitchDriver(String manufacturerDescPrefix,
                                             IOFSwitchDriver driver) {
        if (manufacturerDescPrefix == null) {
            throw new NullPointerException("manufacturerDescrptionPrefix" +
                    " must not be null");
        }
        if (driver == null) {
            throw new NullPointerException("driver must not be null");
        }

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
    public synchronized IOFSwitch
            getOFSwitchInstance(OFDescriptionStatistics description) {
        if (description == null)
            throw new NullPointerException("description must not be null");
        if (description.getHardwareDescription() == null) {
            throw new NullPointerException(
                    "hardware description must not be null");
        }
        if (description.getManufacturerDescription() == null) {
            throw new NullPointerException(
                    "manufacturer description must not be null");
        }
        if (description.getSerialNumber() == null) {
            throw new NullPointerException(
                    "serial number must not be null");
        }
        if (description.getDatapathDescription() == null) {
            throw new NullPointerException(
                    "datapath description must not be null");
        }
        if (description.getSoftwareDescription() == null) {
            throw new NullPointerException(
                    "software description must not be null");
        }


        // Find the appropriate driver
        for (String descPrefix: switchDescSorted) {
            if (description.getManufacturerDescription()
                    .startsWith(descPrefix)) {
                IOFSwitchDriver driver = switchBindingMap.get(descPrefix);
                IOFSwitch sw = driver.getOFSwitchImpl(description);
                if (sw != null) {
                    sw.setSwitchProperties(description);
                    return sw;
                }
            }
        }
        // no switch found
        IOFSwitch sw = new OFSwitchImpl();
        sw.setSwitchProperties(description);
        return sw;
    }

}
