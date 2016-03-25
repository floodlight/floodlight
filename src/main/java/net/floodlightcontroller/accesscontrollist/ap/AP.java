/**
 *    Copyright 2015, Big Switch Networks, Inc.
 *    Originally created by Pengfei Lu, Network and Cloud Computing Laboratory, Dalian University of Technology, China 
 *    Advisers: Keqiu Li, Heng Qi and Haisheng Yu 
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

package net.floodlightcontroller.accesscontrollist.ap;

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
