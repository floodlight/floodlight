/**
 *    Copyright 2015, Big Switch Networks, Inc.
 *    Originally created by Pengfei Lu, Network and Cloud Computing Laboratory, Dalian University of Technology, China 
 *    Advisers: Keqiu Li and Heng Qi 
 *    This work is supported by the State Key Program of National Natural Science of China(Grant No. 61432002) 
 *    and Prospective Research Project on Future Networks in Jiangsu Future Networks Innovation Institute.
 *    
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may 
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

package net.floodlightcontroller.accesscontrollist;

import static net.floodlightcontroller.accesscontrollist.util.IPAddressUtil.isSubnet;

public class ACLRule {

	private int id;
	private String nw_src; // CIDR IP address
	private String nw_dst; // CIDR IP address
	private int nw_src_prefix;
	private int nw_src_maskbits;
	private int nw_dst_prefix;
	private int nw_dst_maskbits;
	private int nw_proto;
	private int tp_dst;
	private Action action;

	public enum Action {
		DENY, ALLOW
	}

	public ACLRule() {
		this.id = 0;
		this.nw_src = null;
		this.nw_dst = null;
		this.nw_src_prefix = 0;
		this.nw_src_maskbits = 0;
		this.nw_dst_prefix = 0;
		this.nw_dst_maskbits = 0;
		this.nw_proto = 0;
		this.tp_dst = 0;
		this.action = Action.DENY;
	}

	/**
	 * check if this rule matches an existing rule:
	 * a matching means this rule won't take effect at all for reason that
	 * its working scope is already covered by an existing rule
	 */
	public boolean match(ACLRule r) {
		if ((this.tp_dst == r.tp_dst || r.tp_dst == 0)
				&& (this.nw_proto == r.nw_proto || r.nw_proto == 0)
				&& (isSubnet(this.getNw_src(), r.getNw_src()) == true)
				&& (isSubnet(this.getNw_dst(), r.getNw_dst()) == true)) {
			return true;
		}
		return false;
	}
	
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public void setNw_src(String nw_src) {
		this.nw_src = nw_src;
	}

	public String getNw_src() {
		return nw_src;
	}

	public void setNw_dst(String nw_dst) {
		this.nw_dst = nw_dst;
	}

	public String getNw_dst() {
		return nw_dst;
	}

	public int getNw_src_prefix() {
		return nw_src_prefix;
	}

	public void setNw_src_prefix(int nw_src_prefix) {
		this.nw_src_prefix = nw_src_prefix;
	}

	public int getNw_src_maskbits() {
		return nw_src_maskbits;
	}

	public void setNw_src_maskbits(int nw_src_maskbits) {
		this.nw_src_maskbits = nw_src_maskbits;
	}

	public int getNw_dst_prefix() {
		return nw_dst_prefix;
	}

	public void setNw_dst_prefix(int nw_dst_prefix) {
		this.nw_dst_prefix = nw_dst_prefix;
	}

	public int getNw_dst_maskbits() {
		return nw_dst_maskbits;
	}

	public void setNw_dst_maskbits(int nw_dst_maskbits) {
		this.nw_dst_maskbits = nw_dst_maskbits;
	}

	public int getNw_proto() {
		return nw_proto;
	}

	/**
	 * @param nw_proto TCP:6, UDP:11, ICMP:1
	 */
	public void setNw_proto(int nw_proto) {
		this.nw_proto = nw_proto;
	}

	public int getTp_dst() {
		return tp_dst;
	}

	public void setTp_dst(int tp_dst) {
		this.tp_dst = tp_dst;
	}

	public Action getAction() {
		return action;
	}

	public void setAction(Action action) {
		this.action = action;
	}


	@Override
	public String toString() {
		return "ACLRule [id=" + id + ", nw_src=" + nw_src + ", nw_dst="
				+ nw_dst + ", nw_src_prefix=" + nw_src_prefix
				+ ", nw_src_maskbits=" + nw_src_maskbits + ", nw_dst_prefix="
				+ nw_dst_prefix + ", nw_dst_maskbits=" + nw_dst_maskbits
				+ ", nw_proto=" + nw_proto + ", tp_dst=" + tp_dst + ", action="
				+ action + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((action == null) ? 0 : action.hashCode());
		result = prime * result + id;
		result = prime * result + ((nw_dst == null) ? 0 : nw_dst.hashCode());
		result = prime * result + nw_dst_maskbits;
		result = prime * result + nw_dst_prefix;
		result = prime * result + nw_proto;
		result = prime * result + ((nw_src == null) ? 0 : nw_src.hashCode());
		result = prime * result + nw_src_maskbits;
		result = prime * result + nw_src_prefix;
		result = prime * result + tp_dst;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof ACLRule))
			return false;
		ACLRule other = (ACLRule) obj;
		if (action != other.action)
			return false;
		if (id != other.id)
			return false;
		if (nw_dst == null) {
			if (other.nw_dst != null)
				return false;
		} else if (!nw_dst.equals(other.nw_dst))
			return false;
		if (nw_dst_maskbits != other.nw_dst_maskbits)
			return false;
		if (nw_dst_prefix != other.nw_dst_prefix)
			return false;
		if (nw_proto != other.nw_proto)
			return false;
		if (nw_src == null) {
			if (other.nw_src != null)
				return false;
		} else if (!nw_src.equals(other.nw_src))
			return false;
		if (nw_src_maskbits != other.nw_src_maskbits)
			return false;
		if (nw_src_prefix != other.nw_src_prefix)
			return false;
		if (tp_dst != other.tp_dst)
			return false;
		return true;
	}
	

}
