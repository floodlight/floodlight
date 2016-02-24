package net.floodlightcontroller.servicechaining;

import net.floodlightcontroller.core.FloodlightContextStore;
import net.floodlightcontroller.core.module.IFloodlightService;

public interface IServiceChainingService extends IFloodlightService {
    /**
     * A FloodlightContextStore object that can be used to interact with the
     * FloodlightContext information created by ServiceInsertion.
     */
    public static final FloodlightContextStore<String> scStore =
        new FloodlightContextStore<String>();

    /**
     * Returns the service chain by source BVS.
     * @param bvsName
     * @return the ServiceChain, null is the requested service is not found.
     */
    public ServiceChain getServiceChainBySrcBVS(String bvsName);

    /**
     * Returns the service chain by destination BVS.
     * @param bvsName
     * @return the ServiceChain, null is the requested service is not found.
     */
    public ServiceChain getServiceChainByDstBVS(String bvsName);
}
