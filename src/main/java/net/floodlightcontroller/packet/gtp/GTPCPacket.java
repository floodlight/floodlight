package net.floodlightcontroller.packet.gtp;

public class GTPCPacket extends AbstractGTP {
	
	public GTPCPacket(){
		super();
		this.setControlPacket(true);
	}

}
