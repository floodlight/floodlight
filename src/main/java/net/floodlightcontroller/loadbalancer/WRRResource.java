package net.floodlightcontroller.loadbalancer;


import org.restlet.resource.Post;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WRRResource extends ServerResource{
	protected static Logger log = LoggerFactory.getLogger(WRRResource.class);
		
	@Put
	@Post
	public String setMemberWeight(){

		String memberId = (String) getRequestAttributes().get("member");
		String weight = (String) getRequestAttributes().get("weight");

		ILoadBalancerService lbs =
				(ILoadBalancerService)getContext().getAttributes().
				get(ILoadBalancerService.class.getCanonicalName());

		return "{\"status\" : \"" + lbs.setMemberWeight(memberId,weight) + "\"}"; // Used by GUI TODO HTTP codes

	}	
}
