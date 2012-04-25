package net.floodlightcontroller.ui.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.restlet.Client;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.data.Protocol;
import org.restlet.resource.Directory;
import org.restlet.routing.Router;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.restserver.RestletRoutable;

public class StaticWebRoutable implements RestletRoutable, IFloodlightModule {

	private IRestApiService restApi;
	
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IRestApiService.class);
        return l;
    }
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        return null;
    }
    
    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        return null;
    }

    @Override
    public void init(FloodlightModuleContext context)
                                             throws FloodlightModuleException {
        restApi = context.getServiceImpl(IRestApiService.class);
    }
    
    @Override
    public void startUp(FloodlightModuleContext context) {
        // Add our REST API
        restApi.addRestletRoutable(new StaticWebRoutable());
        
    }

	@Override
	public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("", new Directory(context, "clap://classloader/web/"));
        context.setClientDispatcher(new Client(context, Protocol.CLAP));
        return router;
	}

	@Override
	public String basePath() {
		return "/ui/";
	}

}
