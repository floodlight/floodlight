package net.floodlightcontroller.core.internal;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchDriver;

import org.openflow.protocol.statistics.OFDescriptionStatistics;

/**
 * Maintain a registry for SwitchDrivers. Drivers can register with the
 * registry and a user can get IOFSwitch instances based on the switch's
 * OFDescriptionStatistics.
 *
 * A driver registers itself by specifying a <i>prefix string</i> of the
 * switch's <i>manufacturer</i> description. When a user request an
 * IOFSwitch instance the registry matches the manufacturer description
 * of the switch against the prefixes in the registry.
 *
 * See getOFSwitchInstance() for a description of the lookup contract
 *
 * @author gregor
 *
 */
public interface ISwitchDriverRegistry {

    /**
     * Register an IOFSwitchDriver with the registry
     *
     * @param manufacturerDescriptionPrefix Register the given prefix
     * with the driver.
     * @param driver A IOFSwitchDriver instance to handle IOFSwitch instaniation
     * for the given manufacturer description prefix
     * @throws IllegalStateException If the the manufacturer description is
     * already registered
     * @throws NullPointerExeption if manufacturerDescriptionPrefix is null
     * @throws NullPointerExeption if driver is null
     */
    void addSwitchDriver(String manufacturerDescriptionPrefix,
                         IOFSwitchDriver driver);
    /**
     * Return an IOFSwitch instance according to the description stats.
     *
     * The driver with the <i>longest matching prefix</i> will be picked first.
     * The description is then handed over to the choosen driver to return an
     * IOFSwitch instance. If the driver does not return an IOFSwitch
     * (returns null) the registry will continue to the next driver with
     * a matching manufacturer description prefix. If no driver returns an
     * IOFSwitch instance the registry returns a default OFSwitchImpl instance.
     *
     * The returned switch will have its description reply and
     * switch properties set according to the DescriptionStats passed in
     *
     * @param description The OFDescriptionStatistics for which to return an
     * IOFSwitch implementation
     * @return A IOFSwitch implementation matching the description or an
     * OFSwitchImpl if no driver returned a more concrete instance.
     * @throws NullPointerException If the OFDescriptionStatistics or any
     * of its members is null.
     */
    IOFSwitch getOFSwitchInstance(OFDescriptionStatistics description);
}
