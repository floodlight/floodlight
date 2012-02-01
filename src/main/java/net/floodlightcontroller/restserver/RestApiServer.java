package net.floodlightcontroller.restserver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.restlet.Application;
import org.restlet.Component;
import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.Protocol;
import org.restlet.data.Reference;
import org.restlet.routing.Filter;
import org.restlet.routing.Router;
import org.restlet.routing.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.web.JacksonCustomConverter;

public class RestApiServer
    implements IFloodlightModule, IRestApiService {
    protected static Logger logger = LoggerFactory.getLogger(RestApiServer.class);
    protected List<RestletRoutable> restlets;
    protected int restPort = 8080;
    protected FloodlightModuleContext fmlContext;
    
    // ***********
    // Application
    // ***********
    
    protected class RestApplication extends Application {
        protected Context context;
        
        public RestApplication() {
            super(new Context());
            this.context = getContext();
        }
        
        @Override
        public Restlet createInboundRoot() {
            Router baseRouter = new Router(context);
            baseRouter.setDefaultMatchingMode(Template.MODE_STARTS_WITH);
            for (RestletRoutable rr : restlets) {
                baseRouter.attach(rr.basePath(), rr.getRestlet(context));
            }

            Filter slashFilter = new Filter() {            
                @Override
                protected int beforeHandle(Request request, Response response) {
                    Reference ref = request.getResourceRef();
                    String originalPath = ref.getPath();
                    if (originalPath.contains("//"))
                    {
                        String newPath = originalPath.replaceAll("/+", "/");
                        ref.setPath(newPath);
                    }
                    return Filter.CONTINUE;
                }

            };
            slashFilter.setNext(baseRouter);
            
            return slashFilter;
        }
        
        public void run(FloodlightModuleContext fmlContext) {
            // Add everything in the module context to the rest
            for (Class<? extends IFloodlightService> s : fmlContext.getAllServices()) {
                context.getAttributes().put(s.getCanonicalName(), 
                                            fmlContext.getServiceImpl(s));
            }
            
            // Use our custom serializers
            JacksonCustomConverter.replaceConverter();
            
            // Start listening for REST requests
            try {
                final Component component = new Component();
                component.getServers().add(Protocol.HTTP, restPort);
                component.getDefaultHost().attach(this);
                component.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    // ***************
    // IRestApiService
    // ***************
    
    @Override
    public void addRestletRoutable(RestletRoutable routable) {
        if (logger.isDebugEnabled()) {
            logger.debug("Adding REST API routable " 
                    + routable.getClass().getCanonicalName());
        }
        restlets.add(routable);
    }
    
    @Override
    public void run() {
        RestApplication restApp = new RestApplication();
        restApp.run(fmlContext);
    }
    
    // *****************
    // IFloodlightModule
    // *****************
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> services =
                new ArrayList<Class<? extends IFloodlightService>>(1);
        services.add(IRestApiService.class);
        return services;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService>
            getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
        IFloodlightService> m = 
            new HashMap<Class<? extends IFloodlightService>,
                        IFloodlightService>();
        m.put(IRestApiService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        // We don't have any
        return null;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        // This has to be done here since we don't know what order the
        // startUp methods will be called
        this.restlets = new ArrayList<RestletRoutable>();
    }

    @Override
    public void startUp(FloodlightModuleContext fmlContext) {
        this.fmlContext = fmlContext;
    }
}