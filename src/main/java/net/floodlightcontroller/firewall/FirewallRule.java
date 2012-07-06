package net.floodlightcontroller.firewall;

public class FirewallRule {
	public int src_inport;
	public String src_mac;
	public String src_ip;
	public String proto_type;
	public String proto_srcport;
	public String proto_dstport;
	public String dst_mac;
	public String dst_ip;
	public String switchid;

	public FirewallRule() {
		this.src_inport = -1;
		this.src_mac = "ANY";
		this.src_ip = "ANY";
		this.proto_type = "ANY";
		this.proto_srcport = "-1";
		this.proto_dstport = "-1";
		this.dst_mac = "ANY";
		this.dst_ip = "ANY";
		this.switchid = "";
	}
}
