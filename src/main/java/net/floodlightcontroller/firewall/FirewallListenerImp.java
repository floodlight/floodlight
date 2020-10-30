/** 
By Benamrane Fouad

*/
package net.floodlightcontroller.firewall;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

public class FirewallListenerImp implements FirewallListener{

	protected static Logger logger = LoggerFactory.getLogger(Firewall.class); 
	private FirewallRule rule;
	private static int listenerIDTracker = 0;
	private int listenerID;
	private Firewall firewallService;
	
	public FirewallListenerImp (Firewall service){
		this.firewallService = service;
		this.listenerID =++listenerIDTracker;
		logger.info("New listener "+this.listenerID);
		service.register(this);
		
	}
	@Override
	public void updateNewRule(FirewallRule R) {
		// TODO Auto-generated method stub
		this.rule =R;
	}

	@Override
	public void updateRemoveRule(FirewallRule R) {
		// TODO Auto-generated method stub
		this.rule = R;
	}

	@Override
	public void firewallActivated() {
		// TODO Auto-generated method stub
		
	}

}
