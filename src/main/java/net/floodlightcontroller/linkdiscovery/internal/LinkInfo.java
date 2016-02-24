/**
 *    Copyright 2011, Big Switch Networks, Inc.*    Originally created by David Erickson, Stanford University
 **    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 **/

package net.floodlightcontroller.linkdiscovery.internal;

import java.util.ArrayDeque;
import java.util.Date;

import org.projectfloodlight.openflow.types.U64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LinkType;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class LinkInfo {
	private static final Logger log = LoggerFactory.getLogger(LinkInfo.class);
	
	private Date firstSeenTime;
	private Date lastLldpReceivedTime; /* Standard LLDP received time */
	private Date lastBddpReceivedTime; /* Modified LLDP received time  */
	private U64 currentLatency;
	private ArrayDeque<U64> latencyHistory;
	private int latencyHistoryWindow;
	private double latencyUpdateThreshold;
	
	public LinkInfo(Date firstSeenTime, Date lastLldpReceivedTime, Date lastBddpReceivedTime) {
		this.firstSeenTime = firstSeenTime;
		this.lastLldpReceivedTime = lastLldpReceivedTime;
		this.lastBddpReceivedTime = lastBddpReceivedTime;
		this.currentLatency = null;
		this.latencyHistory = new ArrayDeque<U64>(LinkDiscoveryManager.LATENCY_HISTORY_SIZE);
		this.latencyHistoryWindow = LinkDiscoveryManager.LATENCY_HISTORY_SIZE;
		this.latencyUpdateThreshold = LinkDiscoveryManager.LATENCY_UPDATE_THRESHOLD;
	}

	public LinkInfo(LinkInfo fromLinkInfo) {
		this.firstSeenTime = fromLinkInfo.getFirstSeenTime();
		this.lastLldpReceivedTime = fromLinkInfo.getUnicastValidTime();
		this.lastBddpReceivedTime = fromLinkInfo.getMulticastValidTime();
		this.currentLatency = fromLinkInfo.currentLatency;
		this.latencyHistory = new ArrayDeque<U64>(fromLinkInfo.getLatencyHistory());
		this.latencyHistoryWindow = fromLinkInfo.getLatencyHistoryWindow();
		this.latencyUpdateThreshold = fromLinkInfo.getLatencyUpdateThreshold();
	}

	/** 
	 * The port states stored here are topology's last knowledge of
	 * the state of the port. This mostly mirrors the state
	 * maintained in the port list in IOFSwitch (i.e. the one returned
	 * from getPort), except that during a port status message the
	 * IOFSwitch port state will already have been updated with the
	 * new port state, so topology needs to keep its own copy so that
	 * it can determine if the port state has changed and therefore
	 * requires the new state to be written to storage.
	 */

	private int getLatencyHistoryWindow() {
		return latencyHistoryWindow;
	}

	private double getLatencyUpdateThreshold() {
		return latencyUpdateThreshold;
	}
	
	private ArrayDeque<U64> getLatencyHistory() {
		return latencyHistory;
	}

	private U64 getLatencyHistoryAverage() {
		if (!isLatencyHistoryFull()) {
			return null;
		} else { /* guaranteed to be at latencyHistoryWindow capacity */
			double avg = 0;
			for (U64 l : latencyHistory) {
				avg = avg + l.getValue();
			}
			avg = avg / latencyHistoryWindow;
			return U64.of((long) avg);
		}
	}
	
	/**
	 * Retrieve the current latency, and if necessary
	 * compute and replace the current latency with an
	 * updated latency based on the historical average.
	 * @return the most up-to-date latency as permitted by algorithm
	 */
	private U64 getLatency() {
		U64 newLatency = getLatencyHistoryAverage();
		if (newLatency != null) {
			/* check threshold */
			if ((((double) Math.abs(newLatency.getValue() - currentLatency.getValue())) 
					/ (currentLatency.getValue() == 0 ? 1 : currentLatency.getValue())
					) 
					>= latencyUpdateThreshold) {
				/* perform update */
				log.debug("Updating link latency from {} to {}", currentLatency.getValue(), newLatency.getValue());
				currentLatency = newLatency;
			}
		}
		return currentLatency;
	}

	/**
	 * Determine if we've observed enough latency values
	 * to consider computing a new latency value based
	 * on the historical average. A minimum history size
	 * must be met prior to updating a latency.
	 * 
	 * @return true if full; false if not full
	 */
	private boolean isLatencyHistoryFull() {
		return (latencyHistory.size() == latencyHistoryWindow);
	}
	
	/**
	 * Append a new (presumably most recent) latency
	 * to the list. Sets the current latency if this
	 * is the first latency update performed. Note
	 * the latter serves as a latency initializer.
	 * 
	 * @param latency
	 * @return latency to use for the link; either initial or historical average
	 */
	public U64 addObservedLatency(U64 latency) {
		if (isLatencyHistoryFull()) {
			latencyHistory.removeFirst();
		}
		latencyHistory.addLast(latency);

		if (currentLatency == null) {
			currentLatency = latency;
			return currentLatency;
		} else {
			return getLatency();
		}
	}
	
	/**
	 * Read-only. Retrieve the currently-assigned
	 * latency for this link. Does not attempt to
	 * update or compute an average.
	 * @return the latency; null if an initial latency has not been set
	 */
	public U64 getCurrentLatency() {
		return currentLatency;
	}

	public Date getFirstSeenTime() {
		return firstSeenTime;
	}

	public void setFirstSeenTime(Date firstSeenTime) {
		this.firstSeenTime = firstSeenTime;
	}

	public Date getUnicastValidTime() {
		return lastLldpReceivedTime;
	}

	public void setUnicastValidTime(Date unicastValidTime) {
		this.lastLldpReceivedTime = unicastValidTime;
	}

	public Date getMulticastValidTime() {
		return lastBddpReceivedTime;
	}

	public void setMulticastValidTime(Date multicastValidTime) {
		this.lastBddpReceivedTime = multicastValidTime;
	}

	@JsonIgnore
	public LinkType getLinkType() {
		if (lastLldpReceivedTime != null) {
			return LinkType.DIRECT_LINK;
		} else if (lastBddpReceivedTime != null) {
			return LinkType.MULTIHOP_LINK;
		}
		return LinkType.INVALID_LINK;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	 @Override
	 public int hashCode() {
		final int prime = 5557;
		int result = 1;
		result = prime * result + ((firstSeenTime == null) ? 0 : firstSeenTime.hashCode());
		result = prime * result + ((lastLldpReceivedTime == null) ? 0 : lastLldpReceivedTime.hashCode());
		result = prime * result + ((lastBddpReceivedTime == null) ? 0 : lastBddpReceivedTime.hashCode());
		return result;
	 }

	 /* (non-Javadoc)
	  * @see java.lang.Object#equals(java.lang.Object)
	  */
	 @Override
	 public boolean equals(Object obj) {
		 if (this == obj)
			 return true;
		 if (obj == null)
			 return false;
		 if (!(obj instanceof LinkInfo))
			 return false;
		 LinkInfo other = (LinkInfo) obj;

		 if (firstSeenTime == null) {
			 if (other.firstSeenTime != null)
				 return false;
		 } else if (!firstSeenTime.equals(other.firstSeenTime))
			 return false;

		 if (lastLldpReceivedTime == null) {
			 if (other.lastLldpReceivedTime != null)
				 return false;
		 } else if (!lastLldpReceivedTime.equals(other.lastLldpReceivedTime))
			 return false;

		 if (lastBddpReceivedTime == null) {
			 if (other.lastBddpReceivedTime != null)
				 return false;
		 } else if (!lastBddpReceivedTime.equals(other.lastBddpReceivedTime))
			 return false;

		 return true;
	 }


	 /* (non-Javadoc)
	  * @see java.lang.Object#toString()
	  */
	 @Override
	 public String toString() {
		 return "LinkInfo [unicastValidTime=" + ((lastLldpReceivedTime == null) ? "null" : lastLldpReceivedTime.getTime())
				 + ", multicastValidTime=" + ((lastBddpReceivedTime == null) ? "null" : lastBddpReceivedTime.getTime())
				 + "]";
	 }
}
