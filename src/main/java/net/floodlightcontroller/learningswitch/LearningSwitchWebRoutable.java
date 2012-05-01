package net.floodlightcontroller.learningswitch;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;

public class LearningSwitchWebRoutable implements RestletRoutable {

    @Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/table/{switch}/json", LearningSwitchTable.class);
        return router;
    }

    @Override
    public String basePath() {
        return "/wm/learningswitch";
    }
}
