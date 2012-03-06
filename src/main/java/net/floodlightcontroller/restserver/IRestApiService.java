package net.floodlightcontroller.restserver;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IRestApiService extends IFloodlightService {
    /**
     * Adds a REST API
     * @param routeable
     */
    public void addRestletRoutable(RestletRoutable routable);

    /**
     * Runs the REST API server
     */
    public void run();
}
