package net.floodlightcontroller.loadbalancer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using=L7PolicySerializer.class)
public class L7Policy {
	protected String id;
	protected String vipId;
	protected String poolId;
	protected short action;
	

	public L7Policy() {
		id = String.valueOf((int) (Math.random()*10000));
		poolId = null;
		vipId = null;
		action = 0;
	}
}
