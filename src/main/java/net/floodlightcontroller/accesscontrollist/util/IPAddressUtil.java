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

package net.floodlightcontroller.accesscontrollist.util;

import net.floodlightcontroller.packet.IPv4;

public class IPAddressUtil {

	/**
	 * parse the given CIDR IP
	 * 
	 * @return an array contains the CIDR prefix and mask bits
	 * 
	 */
	public static int[] parseCIDR(String cidr) {

		int ret[] = new int[2];

		String[] parts = cidr.split("/");
		
		if (parts.length == 1){
			throw new IllegalArgumentException("CIDR mask bits must be specified.");
		}
		
		String cidrPrefix = parts[0].trim();
		int cidrMaskBits = 0;
		if (parts.length == 2) {
			try {
				cidrMaskBits = Integer.parseInt(parts[1].trim());
			} catch (Exception e) {
				throw new NumberFormatException("CIDR mask bits must be specified as a number(0 ~ 32).");
			}
			if (cidrMaskBits < 0 || cidrMaskBits > 32) {
				throw new NumberFormatException("CIDR mask bits must be 0 <= value <= 32.");
			}
		}
		ret[0] = IPv4.toIPv4Address(cidrPrefix);
		ret[1] = cidrMaskBits;

		return ret;
	}

	/**
	 * check whether the CIDR address contains the IP address
	 */
	public static boolean containIP(int cidrPrefix, int cidrMaskBits, int ip) {

		boolean matched = true;
		int bitsToShift = 32 - cidrMaskBits;

		if (bitsToShift > 0) {
			cidrPrefix = cidrPrefix >> bitsToShift;
			ip = ip >> bitsToShift;
			cidrPrefix = cidrPrefix << bitsToShift;
			ip = ip << bitsToShift;
		}

		if (cidrPrefix != ip) {
			matched = false;
		}

		return matched;
	}

	/**
	 * check whether cidr1 is a subnet of (or the same as) cidr2
	 * 
	 */
	public static boolean isSubnet(String cidr1, String cidr2) {

		if (cidr2 == null) {
			return true;
		} else if (cidr1 == null) {
			return false;
		}

		int[] cidr = IPAddressUtil.parseCIDR(cidr1);
		int cidr1Prefix = cidr[0];
		int cidr1MaskBits = cidr[1];
		cidr = IPAddressUtil.parseCIDR(cidr2);
		int cidr2Prefix = cidr[0];
		int cidr2MaskBits = cidr[1];

		int bitsToShift_1 = 32 - cidr1MaskBits;
		int bitsToShift_2 = 32 - cidr2MaskBits;

		int offset = (bitsToShift_1 > bitsToShift_2) ? bitsToShift_1
				: bitsToShift_2;

		if (offset > 0) {
			cidr1Prefix = cidr1Prefix >> offset;
			cidr2Prefix = cidr2Prefix >> offset;
			cidr1Prefix = cidr1Prefix << offset;
			cidr2Prefix = cidr2Prefix << offset;
		}

		if (cidr1Prefix == cidr2Prefix) {
			if (cidr1MaskBits >= cidr2MaskBits) {
				return true;
			}
		}

		return false;
	}

}
