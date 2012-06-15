package net.floodlightcontroller.virtualnetwork;

import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;

public class NoOp extends ServerResource {
	/**
	 * Does nothing and returns 200 OK with a status message
	 * @return status: ok
	 */
	@Get
	@Put
	@Post
	public String noOp(String postdata) {
		setStatus(Status.SUCCESS_OK);
        return "{\"status\":\"ok\"}";
	}
}
