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

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sdnplatform.sync.IStoreClient;
import org.sdnplatform.sync.error.SyncException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.hasupport.ISyncAdapter;

/**
 * This class gets the updates from the Filter Queue and puts them into the
 * SyncDB.
 *
 * The primary key fields are MD5 hashed and the md5hashes are stored under the
 * controller ID which published them. Now each controller can exchange only the
 * md5hashes and stay up to date, and sync the actual update only if needed.
 *
 * Low frequency fields (mentioned in LDUtils): These are the primary key
 * fields, i.e. when the data is viewed as relational data, these fields would
 * be the primary key fields. This mapping is done so that we can avoid
 * duplication of stored data and also improve our write speed to the syncDB as
 * compared to completely denormalized data where we would have to populate a
 * seperate table for each key field.
 *
 * High Frequency Fields (mentioned here): These are fields which vary a lot and
 * are stored in an array in the JSON that is pushed into the DB. The primary
 * key fields or low frequency fields are attached to these fields to help
 * identify which particular NodePortTuple this update belongs to.
 *
 * The data model used to design this system can be found here (similar, not
 * same):
 * https://docs.mongodb.com/v3.2/tutorial/model-referenced-one-to-many-relationships-between-documents
 *
 * Possible improvements: a. Store the collatedcmd5 hashes as a HashMap with
 * keys as the md5hashes and the values as '1' so that retrieval can be
 * optimized. Meaning, lets say you wanted updates for C1, for a particular
 * NodePortTuple, say, 'B' then you first retrieve all the collatedcmd5 hashes
 * associated with C1, which is a string (right now), search for 'B' and if
 * present, you go ahead and look for B in the database, which will then give
 * you the corresponding JSON update for 'B'. Instead if the collatedcmd5 hashes
 * were stored as a hashmap then we wouldn't need to parse the collatedcmd5
 * string. However, the problem with this would be that the size of the HashMap
 * might be a limitation.
 *
 * @author Bhargav Srinivasan, Om Kale
 *
 */

public class LDSyncAdapter implements ISyncAdapter {

	private static final Logger logger = LoggerFactory.getLogger(LDSyncAdapter.class);

	protected static IFloodlightProviderService floodlightProvider;
	private static IStoreClient<String, String> storeLD;

	private static LDFilterQueue myLDFilterQueue;
	private String controllerID;
	private final String none = new String("none");
	private final String[] highfields = new String[] { "operation", "latency", "timestamp" };

	public LDSyncAdapter(IStoreClient<String, String> storeLD, String controllerID, LDFilterQueue ldFilterQueue) {
		LDSyncAdapter.storeLD = storeLD;
		this.controllerID = controllerID;
		LDSyncAdapter.myLDFilterQueue = ldFilterQueue;
	}

	/**
	 * Receives the updates from the FilterQueue's enqueueForward method, and
	 * assembles the JSON object that is to be pushed into the syncDB using
	 * Jackson.
	 *
	 * This method first checks if the incoming update's primary key is already
	 * in the syncDB, if so, it retrieves that particular update and appends the
	 * new values to it along with a timestamp.
	 *
	 * If the incoming update does not exist in the syncDB, it hashes the
	 * primary key or low frequency fields of the update and this forms the
	 * "KEY" for this update in the syncDB. It is appended with a timestamp and
	 * then pushed into the syncDB.
	 *
	 * The MD5Hashes of the primary key fields or "KEY"s are collected in a
	 * String called the collatedmd5hashes. Now this string is pushed into the
	 * syncDB as well, with the corresponding controller ID from which it came
	 * from as key: <C1, collatedmd5hashes>. Now every controller will have
	 * access to the collatedmd5hashes of every other controller, and can hence
	 * retrieve any update from any controller.
	 *
	 */

	@Override
	public void packJSON(List<String> newUpdates) {

		ObjectMapper myMapper = new ObjectMapper();
		TypeReference<HashMap<String, String>> typeRef = new TypeReference<HashMap<String, String>>() {
		};
		Map<String, String> newUpdateMap = new HashMap<>();
		Map<String, String> updateMap = new HashMap<>();
		String cmd5Hash = new String();
		LDHAUtils ldhautils = new LDHAUtils();

		if (newUpdates.isEmpty()) {
			return;
		}

		for (String up : newUpdates) {
			try {

				newUpdateMap = myMapper.readValue(up.toString(), typeRef);
				cmd5Hash = ldhautils.getCMD5Hash(up, newUpdateMap);

				/**
				 * Make the high freq fields as lists.
				 */
				String operation = newUpdateMap.get(highfields[0]);
				String latency = newUpdateMap.get(highfields[1]);
				/**
				 * Add timestamp field.
				 */

				Long ts = new Long(Instant.now().getEpochSecond());
				Long nano = new Long(Instant.now().getNano());

				newUpdateMap.put(highfields[0], operation);
				newUpdateMap.put(highfields[1], latency);
				newUpdateMap.put(highfields[2], ts.toString() + nano.toString());

				/**
				 * Try to get previous update:
				 */
				String oldUpdates = storeLD.getValue(cmd5Hash.toString(), none);

				if (!oldUpdates.equals(none)) {

					if (oldUpdates.isEmpty()) {
						continue;
					}

					/**
					 * parse the Json String into a Map, then query the entries.
					 */
					updateMap = myMapper.readValue(oldUpdates.toString(), typeRef);

					String oldOp = updateMap.get(highfields[0]);
					// logger.debug("++++OLD OP: {}", new Object[] {oldOp});
					String opList = ldhautils.appendUpdate(oldOp, newUpdateMap.get(highfields[0]));
					updateMap.put(highfields[0], opList); // update high freq
															// fields

					String oldLatency = updateMap.get(highfields[1]);
					// logger.debug("++++OLD LATENCY: {}", new Object[]
					// {oldLatency});
					String latList = ldhautils.appendUpdate(oldLatency, newUpdateMap.get(highfields[1]));
					updateMap.put(highfields[1], latList); // update high freq
															// fields

					String oldTimestamp = updateMap.get(highfields[2]);
					// logger.debug("++++OLD TS: {}", new Object[]
					// {oldTimestamp});
					Long ts2 = new Long(Instant.now().getEpochSecond());
					Long nano2 = new Long(Instant.now().getNano());
					String tmList = ldhautils.appendUpdate(oldTimestamp, ts2.toString() + nano2.toString());
					updateMap.put(highfields[2], tmList);

					LDSyncAdapter.storeLD.put(cmd5Hash.toString(), myMapper.writeValueAsString(updateMap));

				} else {

					try {

						LDSyncAdapter.storeLD.put(cmd5Hash.toString(), myMapper.writeValueAsString(newUpdateMap));

						String collatedcmd5 = LDSyncAdapter.storeLD.getValue(controllerID.toString(), none);

						if (collatedcmd5.equals(none)) {
							collatedcmd5 = cmd5Hash;
							// logger.info("Collated CMD5: {} ", new Object []
							// {collatedcmd5.toString()});
						} else {
							// logger.debug("================ Append update to
							// HashMap ================");
							collatedcmd5 = ldhautils.appendUpdate(collatedcmd5, cmd5Hash);
						}

						LDSyncAdapter.storeLD.put(controllerID, collatedcmd5);

					} catch (SyncException se) {
						logger.debug("[LDSync] Exception: sync packJSON!");
						se.printStackTrace();
					} catch (Exception e) {
						logger.debug("[LDSync] Exception: packJSON!");
						e.printStackTrace();
					}
				}

			} catch (SyncException se) {
				logger.debug("[LDSync] Exception: sync packJSON!");
				se.printStackTrace();
			} catch (Exception e) {
				logger.debug("[LDSync] Exception: packJSON!");
				e.printStackTrace();
			}
		}

	}

	/**
	 * This method is called by the subscribe function in FilterQueue, which
	 * initiates the retrieval of data from the syncDB. The FilterQueue is then
	 * populated with the updates, using the enqueueReverse() method, which is
	 * later read by the subscribe hook.
	 *
	 * It first retrieves the collatedmd5hashes, as explained above, for a
	 * particular controller, and then retrieves the actual updates.
	 *
	 */

	@Override
	public void unpackJSON(String controllerID) {
		/**
		 * Get all cmd5 hashes for the particular controller ID:
		 */

		try {
			String collatedcmd5 = LDSyncAdapter.storeLD.getValue(controllerID, none);

			if (!collatedcmd5.equals(none)) {

				if (collatedcmd5.endsWith(", ")) {
					collatedcmd5 = collatedcmd5.substring(0, collatedcmd5.length() - 2);
				}

				// logger.info("[Unpack] Collated CMD5: {}", new Object[]
				// {collatedcmd5.toString()});

				String[] cmd5hashes = collatedcmd5.split(", ");
				for (String cmd5 : cmd5hashes) {
					String update = LDSyncAdapter.storeLD.getValue(cmd5, none);
					if (!update.equals(none)) {
						// logger.info("[Unpack]: {}", new Object []
						// {update.toString()});
						LDSyncAdapter.myLDFilterQueue.enqueueReverse(update);
					}
				}
			}

			return;

		} catch (SyncException e) {
			e.printStackTrace();
		}

	}

}
