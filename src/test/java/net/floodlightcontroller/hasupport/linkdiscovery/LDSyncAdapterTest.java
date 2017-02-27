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

import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.sdnplatform.sync.IStoreClient;

/**
 * Unit tests for the Sync Adapter class. Test both packing and unpacking of the
 * JSON blobs. Should be able to successfully use the SyncDB to push files.
 *
 * @author Bhargav Srinivasan
 *
 */
public class LDSyncAdapterTest {

	protected static IStoreClient<String, String> storeLD;
	protected static String controllerID = "none";

	@Test
	public void testPackJSON() {
		try {
			List<String> updates = Arrays.asList(
					"{\"src\":\"00:00:00:00:00:00:00:01\",\"operation\":\"Switch Updated\"}",
					"{\"src\":\"00:00:00:00:00:00:00:01\",\"srcPort\":\"1\",\"operation\":\"Port Up\"}",
					"{\"src\":\"00:00:00:00:00:00:00:01\",\"srcPort\":\"2\",\"operation\":\"Port Up\"}",
					"{\"src\":\"00:00:00:00:00:00:00:01\",\"srcPort\":\"local\",\"operation\":\"Port Up\"}",
					"{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"local\",\"operation\":\"Switch Updated\"}",
					"{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"1\",\"operation\":\"Port Up\"}",
					"{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"2\",\"operation\":\"Port Up\"}",
					"{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"3\",\"operation\":\"Port Up\"}",
					"{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"local\",\"operation\":\"Port Up\"}",
					"{\"dstPort\":\"2\",\"dst\":\"00:00:00:00:00:00:00:01\",\"src\":\"00:00:00:00:00:00:00:02\",\"latency\":\"0x0000000000000177\",\"srcPort\":\"2\",\"type\":\"external\",\"operation\":\"Link Updated\"}",
					"{\"dstPort\":\"2\",\"dst\":\"00:00:00:00:00:00:00:02\",\"src\":\"00:00:00:00:00:00:00:01\",\"latency\":\"0x0000000000000177\",\"srcPort\":\"2\",\"type\":\"external\",\"operation\":\"Link Updated\"}");
			LDFilterQueue ldfq = new LDFilterQueue(storeLD, controllerID);
			controllerID = new String("C1");

			for (String upd : updates) {
				try {
					ldfq.enqueueForward(upd);
					ldfq.dequeueForward();
				} catch (NullPointerException ne) {
				}
			}
		} catch (Exception e) {
		}
	}

	@Test
	public void testPackJSON2() {
		try {
			List<String> updates = Arrays.asList(
					"{\"src\":\"00:00:00:00:00:00:00:01\",\"op00:00:00:01\",\"srcPort\":\"1\",\"opera Up\"}:\"00:00:00:00:00:00:00:01\",\"srcPort\":\"2\",\"operation\":\"Port Up\"}",
					"{\"src\":\"00:00:00:00:00:00:00:01\",p\"}",
					"{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"local\",\"operation\":\"Switch Updated\"}",
					"{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"1\",\"operation\":\"Port Up\"}",
					"{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"2\",\"operation\":\"Port Up\"}",
					"{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"3\",\"operation\":\"Port Up\"}",
					"{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"local\",\"operation\":\"Port Up\"}",
					"{\"dstPort\":\"2\",\"dst\":\"00:00:00:00:00:00:00:01\",\"src\":\"00:00:00:00:00:00:00:02\",\"latency\":\"0x0000000000000177\",\"srcPort\":\"2\",\"type\":\"external\",\"operation\":\"Link Updated\"}",
					"{\"dstPort\":\"2\",\"dst\":\"00:00:00:00:00:00:00:02\",\"src\":\"00:00:00:00:00:00:00:01\",\"latency\":\"0x0000000000000177\",\"srcPort\":\"2\",\"type\":\"external\",\"operation\":\"Link Updated\"}");
			LDFilterQueue ldfq = new LDFilterQueue(storeLD, controllerID);
			controllerID = new String("C1");

			for (String upd : updates) {
				ldfq.enqueueForward(upd);
				ldfq.dequeueForward();
			}
		} catch (NullPointerException e) {
		} catch (Exception e) {
			fail("Other exceptions, than the ones expected");
		}
	}

	@Test
	public void testUnpackJSON() {
		try {
			List<String> updates = Arrays.asList(
					"{\"src\":\"00:00:00:00:00:00:00:01\",\"operation\":\"Switch Updated\"}",
					"{\"src\":\"00:00:00:00:00:00:00:01\",\"srcPort\":\"1\",\"operation\":\"Port Up\"}",
					"{\"src\":\"00:00:00:00:00:00:00:01\",\"srcPort\":\"2\",\"operation\":\"Port Up\"}",
					"{\"src\":\"00:00:00:00:00:00:00:01\",\"srcPort\":\"local\",\"operation\":\"Port Up\"}",
					"{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"local\",\"operation\":\"Switch Updated\"}",
					"{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"1\",\"operation\":\"Port Up\"}",
					"{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"2\",\"operation\":\"Port Up\"}",
					"{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"3\",\"operation\":\"Port Up\"}",
					"{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"local\",\"operation\":\"Port Up\"}",
					"{\"dstPort\":\"2\",\"dst\":\"00:00:00:00:00:00:00:01\",\"src\":\"00:00:00:00:00:00:00:02\",\"latency\":\"0x0000000000000177\",\"srcPort\":\"2\",\"type\":\"external\",\"operation\":\"Link Updated\"}",
					"{\"dstPort\":\"2\",\"dst\":\"00:00:00:00:00:00:00:02\",\"src\":\"00:00:00:00:00:00:00:01\",\"latency\":\"0x0000000000000177\",\"srcPort\":\"2\",\"type\":\"external\",\"operation\":\"Link Updated\"}");
			LDFilterQueue ldfq = new LDFilterQueue(storeLD, controllerID);
			controllerID = new String("C1");

			for (String upd : updates) {
				ldfq.enqueueForward(upd);
				ldfq.dequeueForward();
			}
			ldfq.subscribe("C1");

		} catch (NullPointerException e) {
		} catch (Exception e) {
			fail("Other exceptions, than the ones expected");
		}
	}

	@Test
	public void testUnpackJSON2() {
		try {
			List<String> updates = Arrays.asList(
					"{\"src\":\"00:00:00:00:00:00:00:01\",\"operation\":\"Switch Updated\"}",
					"{\"src\":\"00:00:00:00:00:00:00:01\",\"srcPort\":\"1\",\"operation\":\"Port Up\"}",
					"{\"src\":\"00:00:00:00:00:00:00:01\",\"srcPort\":\"2\",\"operation\":\"Port Up\"}",
					"{\"src\":\"00:00:00:00:00:00:00:01\",\"srcPort\":\"local\",\"operation\":\"Port Up\"}",
					"{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"local\",\"operation\":\"Switch Updated\"}",
					"{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"1\",\"operation\":\"Port Up\"}",
					"{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"2\",\"operation\":\"Port Up\"}",
					"{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"3\",\"operation\":\"Port Up\"}",
					"{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"local\",\"operation\":\"Port Up\"}",
					"{\"dstPort\":\"2\",\"dst\":\"00:00:00:00:00:00:00:01\",\"src\":\"00:00:00:00:00:00:00:02\",\"latency\":\"0x0000000000000177\",\"srcPort\":\"2\",\"type\":\"external\",\"operation\":\"Link Updated\"}",
					"{\"dstPort\":\"2\",\"dst\":\"00:00:00:00:00:00:00:02\",\"src\":\"00:00:00:00:00:00:00:01\",\"latency\":\"0x0000000000000177\",\"srcPort\":\"2\",\"type\":\"external\",\"operation\":\"Link Updated\"}");
			LDFilterQueue ldfq = new LDFilterQueue(storeLD, controllerID);
			controllerID = new String("C1");

			for (String upd : updates) {
				ldfq.enqueueForward(upd);
				ldfq.dequeueForward();
			}

			ldfq.subscribe("C1");

		} catch (NullPointerException e) {
		} catch (Exception e) {
			fail("Other exceptions, than the ones expected");
		}
	}

}
