package net.floodlightcontroller.statistics;


import org.projectfloodlight.openflow.types.U64;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import net.floodlightcontroller.statistics.web.FlowRuleStatsSerializer;

@JsonSerialize(using=FlowRuleStatsSerializer.class)
public class FlowRuleStats {
	
	private U64 byteCount;
	private U64 packetCount;
	private int priority;
	private int hardTimeout;
	private int idleTimeout;
	private long durationSec;
	
	private FlowRuleStats(U64 bytes, U64 packets, int priority, int hardTimeout, int idleTimeout,long durationSec) {
		this.byteCount = bytes;
		this.packetCount = packets;
		this.priority = priority;
		this.hardTimeout = hardTimeout;
		this.idleTimeout = idleTimeout;
		this.durationSec = durationSec;
	}
	

	public U64 getByteCount() {
		return byteCount;
	}

	public void setByteCount(U64 byteCount) {
		this.byteCount = byteCount;
	}

	public U64 getPacketCount() {
		return packetCount;
	}

	public void setPacketCount(U64 packetCount) {
		this.packetCount = packetCount;
	}
	
	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}
	
	public long getDurationSec() {
		return durationSec;
	}

	public void setDurationSec(long durationSec) {
		this.durationSec = durationSec;
	}
	
	public int getHardTimeout() {
		return hardTimeout;
	}

	public void setHardTimeout(int hardTimeout) {
		this.hardTimeout = hardTimeout;
	}

	public int getIdleTimeout() {
		return idleTimeout;
	}

	public void setIdleTimeout(int idleTimeout) {
		this.idleTimeout = idleTimeout;
	}
	public static FlowRuleStats of(U64 bytes, U64 packets, int priority, int hardTimeout, int idleTimeout, long durationSec) {
		if (bytes == null) {
			throw new IllegalArgumentException("Bytes cannot be null");
		}
		if (packets == null) {
			throw new IllegalArgumentException("Packets cannot be null");
		}
		return new FlowRuleStats(bytes,packets,priority,hardTimeout,idleTimeout,durationSec);
	}
	
//	@Override
//	public boolean equals(Object obj) {
//		if (this == obj)
//			return true;
//		if (obj == null)
//			return false;
//		if (getClass() != obj.getClass())
//			return false;
//		FlowRuleStats other = (FlowRuleStats) obj;
//		if (byteCount == null) {
//			if (other.byteCount != null)
//				return false;
//		} else if (!byteCount.equals(other.byteCount))
//			return false;
//		if (packetCount == null) {
//			if (other.packetCount != null)
//				return false;
//		} else if (!packetCount.equals(other.packetCount))
//			return false;
//		return true;
//	}
}