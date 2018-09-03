package net.floodlightcontroller.statistics;

import java.util.Set;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import net.floodlightcontroller.statistics.web.PortDescSerializer;

import org.projectfloodlight.openflow.protocol.*;

@JsonSerialize(using=PortDescSerializer.class)
public class PortDesc {
	private DatapathId id;
	private OFPort pt;
	private String name;
	private Set<OFPortState> state;
	private Set<OFPortConfig> config;
	private boolean isUp;
	
	
	private PortDesc() {}
	private PortDesc(DatapathId d, OFPort p, String n, Set<OFPortState> state, Set<OFPortConfig> config, boolean isUp) {
		id = d;
		pt = p;
		name = n;
		this.state = state;
		this.config = config;
		this.isUp = isUp;
	}
	
	public static PortDesc of(DatapathId d, OFPort p, String n, Set<OFPortState> state, Set<OFPortConfig> config, boolean isUp) {
		if (d == null) {
			throw new IllegalArgumentException("Datapath ID cannot be null");
		}
		if (p == null) {throw new IllegalArgumentException("Port cannot be null");
		}
		if (state == null) {
			throw new IllegalArgumentException("State of port cannot be null");
		}
		if (config == null) {
			throw new IllegalArgumentException("Config cannot be null");
		}
		if (n == null) {
			throw new IllegalArgumentException("Name cannot be null");
		}
		return new PortDesc(d, p, n, state, config, isUp);
	}
	
	public DatapathId getId() {
		return id;
	}
	
	public OFPort getPt() {
		return pt;
	}
	
	public Set<OFPortState> getState() {
		return state;
	}
	
	public Set<OFPortConfig> getConfig() {
		return config;
	}
	public DatapathId getSwitchId() {
		return id;
	}
	
	public OFPort getSwitchPort() {
		return pt;
	}	
	
	public String getName() {
		return name;
	}	

	public boolean isUp() {
		return isUp;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((pt == null) ? 0 : pt.hashCode());
		return result;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PortDesc other = (PortDesc) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (pt == null) {
			if (other.pt != null)
				return false;
		} else if (!pt.equals(other.pt))
			return false;
		return true;
	}
}