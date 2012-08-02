package net.floodlightcontroller.firewall;

public class FirewallRule implements Comparable {
	public int ruleid;
	public short src_inport;
	public long src_mac;
	public int src_ip_prefix;
	public int src_ip_bits;
	public short proto_type;
	public short proto_srcport;
	public short proto_dstport;
	public long dst_mac;
	public int dst_ip_prefix;
	public int dst_ip_bits;
	public long switchid;
	public int priority = -1;
	public boolean is_denyrule;

	public FirewallRule() {
		this.src_inport = -1;
		this.src_mac = -1;
		this.src_ip_prefix = 0;
		this.src_ip_bits = 32;
		this.proto_type = 0;
		this.proto_srcport = 0;
		this.proto_dstport = 0;
		this.dst_mac = -1;
		this.dst_ip_prefix = 0;
		this.dst_ip_bits = 32;
		this.switchid = -1;
		this.priority = -1;
		this.is_denyrule = false;
	}
	
	public int compareTo(Object rule) {
        return this.priority - ((FirewallRule)rule).priority;
    }
}
