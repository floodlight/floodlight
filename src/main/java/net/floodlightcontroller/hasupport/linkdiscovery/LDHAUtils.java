/**
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

package net.floodlightcontroller.hasupport.linkdiscovery;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * This is a utility class that consists of utility/support functions that are
 * used for certain tasks by different Link discovery classes Specifically
 * consists of functions to parse the Link discovery updates and calculating md5
 * hashes, the updates are parsed in O(n) time to get the JSON equivalent.
 * 
 * @author Bhargav Srinivasan, Om Kale
 *
 */

public class LDHAUtils {

	private static final Logger logger = LoggerFactory.getLogger(LDHAUtils.class);

	/**
	 * These are the primary key / low frequency fields, which are used to index
	 * the updates in the syncDB.
	 */

	private final String[] lowfields = new String[] { "src", "srcPort", "dst", "dstPort", "type" };

	/**
	 * Sticks the two updates together in a comma separated manner.
	 *
	 * @param oldUpdate
	 *            : Update retrieved from the syncDB (existing value).
	 * @param newUpdate
	 *            : Incoming update (new value).
	 * @return : "oldUpdate, newUpdate"
	 */

	public String appendUpdate(String oldUpdate, String newUpdate) {

		StringBuilder updated = new StringBuilder();

		updated.append(oldUpdate);
		updated.append(", ");
		updated.append(newUpdate);

		return updated.toString();

	}

	/**
	 * Calculates the MD5 hash of the given String.
	 *
	 * @param Input
	 *            String.
	 * @return MD5 hash of the given String.
	 */

	public String calculateMD5Hash(String value) {
		String md5 = new String();
		try {
			MessageDigest m = MessageDigest.getInstance("MD5");
			m.reset();
			m.update(value.getBytes());
			byte[] digest = m.digest();
			BigInteger bigInt = new BigInteger(1, digest);
			md5 = bigInt.toString(16);
		} catch (NoSuchAlgorithmException e) {
			logger.debug("[cmd5Hash] Error in EnqueueFwd!");
			e.printStackTrace();
		}
		return md5;
	}

	/**
	 * MD5 hash of the primary key / low frequency fields for the given updates.
	 * This is used by the packJSON method in order to form the "KEY" for
	 * pushing into the syncDB.
	 *
	 * @param update:
	 *            String representation of the JSON form of the LDUpdate.
	 * @param newUpdateMap:
	 *            A hashmap which has the <"field", "value"> pairs of the JSON
	 *            representation of the incoming updates in the packJSON method.
	 * @return MD5 hash of the primary key fields in the supplied update.
	 */

	public String getCMD5Hash(String update, Map<String, String> newUpdateMap) {
		List<String> cmd5fields = new ArrayList<>();
		String cmd5 = new String();
		/**
		 * check map for low freq updates
		 */
		for (String lf : lowfields) {
			if (newUpdateMap.containsKey(lf)) {
				cmd5fields.add(newUpdateMap.get(lf));
			}
		}

		/**
		 * cmd5fields will contain all low freq field values; take md5 hash of
		 * all values together.
		 */

		StringBuilder md5valuesb = new StringBuilder();
		for (String t : cmd5fields) {
			md5valuesb.append(t);
		}
		String md5values = new String();
		md5values = md5valuesb.toString();

		try {
			LDHAUtils myCMD5 = new LDHAUtils();
			cmd5 = myCMD5.calculateMD5Hash(md5values);
			// logger.debug("[cmd5Hash] The MD5: {} The Value {}", new Object []
			// {cmd5,md5values.toString()}); //use md5values instead of updates.
		} catch (Exception e) {
			logger.debug("[cmd5Hash] Exception: enqueueFwd!");
			e.printStackTrace();
		}
		return cmd5;
	}

	/**
	 * A parser which takes the list of updates as one continuous string and
	 * makes an O(n) pass over them to convert them to a list of JSON objects
	 * which can then be pushed into the FilterQueue.
	 *
	 * @param chunk:
	 *            Continuous String representation of an array of LDUpdates.
	 * @return List of JSON representation of the LDUpdates which were input.
	 */

	public List<String> parseChunk(String chunk) {

		ObjectMapper mapper = new ObjectMapper();
		Map<String, Object> newJson = new HashMap<>();
		List<String> jsonInString = new LinkedList<>();

		String op = new String();
		String src = new String();
		String srcPort = new String();
		String dst = new String();
		String dstPort = new String();
		String latency = new String();
		String type = new String();

		if (!chunk.startsWith("LDUpdate [")) {
			return jsonInString;
		}

		try {
			while (!chunk.equals("]]")) {

				/**
				 * pre
				 */
				if (chunk.startsWith("LDUpdate [")) {
					chunk = chunk.substring(10, chunk.length());
				}
				// logger.debug("\n[Assemble Update] Chunk pre: {}", new
				// Object[] {chunk});

				/**
				 * process keywords
				 */

				/**
				 * field: operation
				 */
				if (chunk.startsWith("operation=")) {
					chunk = chunk.substring(10, chunk.length());
					op = chunk.split(",|]")[0];
					// logger.debug("[Assemble Update] Operation=: {}", new
					// Object[]{op});
					chunk = chunk.substring(op.length(), chunk.length());
				}

				if (chunk.startsWith(", ")) {
					chunk = chunk.substring(2, chunk.length());
				}

				// logger.debug("\n[Assemble Update] Chunk keywords: {}", new
				// Object[] {chunk});

				/**
				 * field: src
				 */
				if (chunk.startsWith("src=")) {
					chunk = chunk.substring(4, chunk.length());
					src = chunk.split(",|]")[0];
					// logger.debug("[Assemble Update] Src=: {}", new
					// Object[]{src});
					chunk = chunk.substring(src.length(), chunk.length());
				}

				if (chunk.startsWith(", ")) {
					chunk = chunk.substring(2, chunk.length());
				}

				// logger.debug("\n[Assemble Update] Chunk keywords: {}", new
				// Object[] {chunk});

				// field: srcPort
				if (chunk.startsWith("srcPort=")) {
					chunk = chunk.substring(8, chunk.length());
					srcPort = chunk.split(",|]")[0];
					// logger.debug("[Assemble Update] SrcPort=: {}", new
					// Object[]{srcPort});
					chunk = chunk.substring(srcPort.length(), chunk.length());
				}

				if (chunk.startsWith(", ")) {
					chunk = chunk.substring(2, chunk.length());
				}

				// logger.debug("\n[Assemble Update] Chunk keywords: {}", new
				// Object[] {chunk});

				/**
				 * field: dst
				 */
				if (chunk.startsWith("dst=")) {
					chunk = chunk.substring(4, chunk.length());
					dst = chunk.split(",|]")[0];
					// logger.debug("[Assemble Update] Dst=: {}", new
					// Object[]{dst});
					chunk = chunk.substring(dst.length(), chunk.length());
				}

				if (chunk.startsWith(", ")) {
					chunk = chunk.substring(2, chunk.length());
				}

				// logger.debug("\n[Assemble Update] Chunk keywords: {}", new
				// Object[] {chunk});

				/**
				 * field: dstPort
				 */
				if (chunk.startsWith("dstPort=")) {
					chunk = chunk.substring(8, chunk.length());
					dstPort = chunk.split(",|]")[0];
					// logger.debug("[Assemble Update] DstPort=: {}", new
					// Object[]{dstPort});
					chunk = chunk.substring(dstPort.length(), chunk.length());
				}

				if (chunk.startsWith(", ")) {
					chunk = chunk.substring(2, chunk.length());
				}

				// logger.debug("\n[Assemble Update] Chunk keywords: {}", new
				// Object[] {chunk});

				/**
				 * field: latency
				 */
				if (chunk.startsWith("latency=")) {
					chunk = chunk.substring(8, chunk.length());
					latency = chunk.split(",|]")[0];
					// logger.debug("[Assemble Update] Latency=: {}", new
					// Object[]{latency});
					chunk = chunk.substring(latency.length(), chunk.length());
				}

				if (chunk.startsWith(", ")) {
					chunk = chunk.substring(2, chunk.length());
				}

				// logger.debug("\n[Assemble Update] Chunk keywords: {}", new
				// Object[] {chunk});

				/**
				 * field: type
				 */
				if (chunk.startsWith("type=")) {
					chunk = chunk.substring(5, chunk.length());
					type = chunk.split(",|]")[0];
					// logger.debug("[Assemble Update] Type=: {}", new
					// Object[]{type});
					chunk = chunk.substring(type.length(), chunk.length());
				}

				if (chunk.startsWith(", ")) {
					chunk = chunk.substring(2, chunk.length());
				}

				// logger.debug("\n[Assemble Update] Chunk keywords: {}", new
				// Object[] {chunk});

				/**
				 * post
				 */
				if (chunk.startsWith("], ")) {
					chunk = chunk.substring(3, chunk.length());
				}
				// logger.debug("\n[Assemble Update] Chunk post: {}", new
				// Object[] {chunk});

				if (!op.isEmpty()) {
					newJson.put("operation", op);
				}
				if (!src.isEmpty()) {
					newJson.put("src", src);
				}
				if (!srcPort.isEmpty()) {
					newJson.put("srcPort", srcPort);
				}
				if (!dst.isEmpty()) {
					newJson.put("dst", dst);
				}
				if (!dstPort.isEmpty()) {
					newJson.put("dstPort", dstPort);
				}
				if (!latency.isEmpty()) {
					newJson.put("latency", latency);
				}
				if (!type.isEmpty()) {
					newJson.put("type", type);
				}

				try {
					jsonInString.add(mapper.writeValueAsString(newJson));
				} catch (JsonProcessingException e) {
					e.printStackTrace();
				}

			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return jsonInString;
	}

}
