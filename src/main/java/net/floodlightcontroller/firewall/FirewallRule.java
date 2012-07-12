package net.floodlightcontroller.firewall;

public class FirewallRule implements Comparable {
	public short src_inport;
	public long src_mac;
	public String src_ip;
	public String proto_type;
	public short proto_srcport;
	public short proto_dstport;
	public long dst_mac;
	public String dst_ip;
	public long switchid;
	public int priority = -1;

	public FirewallRule() {
		this.src_inport = -1;
		this.src_mac = -1;
		this.src_ip = "ANY";
		this.proto_type = "ANY";
		this.proto_srcport = 0;
		this.proto_dstport = 0;
		this.dst_mac = -1;
		this.dst_ip = "ANY";
		this.switchid = -1;
		this.priority = -1;
	}
	
	public int compareTo(Object rule) {
        return this.priority - ((FirewallRule)rule).priority;
    }
}
