package net.floodlightcontroller.simpleft;

import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.IOFSwitch;

import org.projectfloodlight.openflow.protocol.OFControllerRole;
import org.projectfloodlight.openflow.protocol.OFRoleReply;
import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.ListenableFuture;

public class UtilDurable {
	private static Logger logger;
	
	public UtilDurable(){
		logger = LoggerFactory.getLogger(UtilDurable.class);
	}
	
	public OFRoleReply setSwitchRole(IOFSwitch sw, OFControllerRole role) {
		try {	
			ListenableFuture<OFRoleReply> future = sw.writeRequest(sw.getOFFactory().buildRoleRequest()
					.setGenerationId(U64.ZERO)
					.setRole(role)
					.build());
			return future.get(10, TimeUnit.SECONDS);

		} catch (Exception e) {
			logger.error("Failure setting switch {} role to {}.", sw.toString(), role.toString());
			logger.error(e.getMessage());
		}
		return null;
	}

}
