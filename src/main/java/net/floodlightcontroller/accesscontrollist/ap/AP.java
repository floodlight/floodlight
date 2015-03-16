package net.floodlightcontroller.accesscontrollist.ap;

/**
 * Accessing Pair (AP)
 * @author Alex
 *
 */
public class AP {

	private String ip;
	private String dpid;

	public AP(String ip, String dpid) {
		super();
		this.ip = ip;
		this.dpid = dpid;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getDpid() {
		return dpid;
	}

	public void setDpid(String dpid) {
		this.dpid = dpid;
	}


	@Override
	public String toString() {
		return "AP [ip=" + ip + ", dpid=" + dpid + "]";
	}

}
