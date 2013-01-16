package org.openflow.protocol.factory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Singleton registry object that holds a mapping from vendor ids to vendor-specific
 *  mapping factories. Threadsafe.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class OFVendorActionRegistry {
    private static class InstanceHolder {
        private final static OFVendorActionRegistry instance = new OFVendorActionRegistry();
    }

    public static OFVendorActionRegistry getInstance() {
        return InstanceHolder.instance;
    }
    private final Map <Integer, OFVendorActionFactory> vendorActionFactories;

    public OFVendorActionRegistry() {
        vendorActionFactories = new ConcurrentHashMap<Integer, OFVendorActionFactory>();
    }

    public OFVendorActionFactory register(int vendorId, OFVendorActionFactory factory) {
        return vendorActionFactories.put(vendorId, factory);
    }

    public OFVendorActionFactory get(int vendorId) {
        return vendorActionFactories.get(vendorId);
    }


}
