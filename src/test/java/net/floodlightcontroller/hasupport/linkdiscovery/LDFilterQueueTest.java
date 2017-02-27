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

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sdnplatform.sync.IStoreClient;

public class LDFilterQueueTest {

	protected static IStoreClient<String, String> storeLD;
	protected static String controllerID = "none";

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testDequeueForward() {
		LDFilterQueue ldf2 = new LDFilterQueue(storeLD, controllerID);
		String testJson = new String("{\"src\":\"00:00:00:00:00:00:00:05\",\"operation\":\"Switch Removed\"}");
		assertEquals(ldf2.enqueueForward(testJson), true);
		ldf2.dequeueForward();
		assertEquals(LDFilterQueue.filterQueue.size(), 0);
		LDFilterQueue.myMap.clear();
		LDFilterQueue.filterQueue.clear();
	}

	@Test
	public void testDequeueForward2() {
		LDFilterQueue ldf2 = new LDFilterQueue(storeLD, controllerID);
		assertEquals(ldf2.dequeueForward(), false);
	}

	@Test
	public void testEnqueueForward() {
		LDFilterQueue ldf = new LDFilterQueue(storeLD, controllerID);
		assertEquals(ldf.enqueueForward("cat"), true);
		assertEquals(LDFilterQueue.myMap.get("d077f244def8a70e5ea758bd8352fcd8"), "cat");
		LDFilterQueue.myMap.clear();
		LDFilterQueue.filterQueue.clear();
	}

	@Test
	public void testEnqueueForward2() {
		LDFilterQueue ldf2 = new LDFilterQueue(storeLD, controllerID);
		String testJson = new String("{\"src\":\"00:00:00:00:00:00:00:05\",\"operation\":\"Switch Removed\"}");
		assertEquals(ldf2.enqueueForward(testJson), true);
		assertEquals(LDFilterQueue.myMap.get("f6816a638cbd1fcec9dcd88ebc2cfcb0"), testJson);
		assertEquals(LDFilterQueue.myMap.get("f6816a638cbd1fcec9dcd88bc2cfcb0"), null);
		assertEquals(LDFilterQueue.myMap.size(), 1);
		LDFilterQueue.myMap.clear();
		LDFilterQueue.filterQueue.clear();
	}

	@Test
	public void testReverse() {
		LDFilterQueue ldf2 = new LDFilterQueue(storeLD, controllerID);
		String testJson = new String("{\"src\":\"00:00:00:00:00:00:00:05\",\"operation\":\"Switch Removed\"}");
		assertEquals(ldf2.enqueueReverse(testJson), true);
		ldf2.dequeueReverse();
		assertEquals(LDFilterQueue.reverseFilterQueue.size(), 0);
		LDFilterQueue.reverseFilterQueue.clear();
	}

	@Test
	public void testReverse2() {
		LDFilterQueue ldf2 = new LDFilterQueue(storeLD, controllerID);
		String testJson = new String("cat{\"src\":\"00:00:00:00:00:00::\"Switch Removed\"}");
		assertEquals(ldf2.enqueueReverse(testJson), true);
		ldf2.dequeueReverse();
		assertEquals(LDFilterQueue.reverseFilterQueue.size(), 0);
		LDFilterQueue.reverseFilterQueue.clear();
	}

}
