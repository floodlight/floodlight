package net.floodlightcontroller.core;

import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.web.RestletRoutable;

public interface IRestApiService extends IFloodlightService {
    /**
     * Adds a REST API
     * @param routeable
     */
    public void addRestApi(RestletRoutable routeable,
                           Class<? extends IFloodlightService> service,
                           IFloodlightModule module);
}
