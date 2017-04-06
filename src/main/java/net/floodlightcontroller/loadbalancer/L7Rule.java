package net.floodlightcontroller.loadbalancer;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using=L7RuleSerializer.class)
public class L7Rule {
	protected String id;
	protected String policyId;
	protected short type;
	protected String value;
	
	
	public L7Rule() {
		id = String.valueOf((int) (Math.random()*10000));
		policyId = null;
		value = null;
		type = 0;
	}
}
