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

package net.floodlightcontroller.hasupport.topology;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sdnplatform.sync.IStoreClient;

public class TopoFilterQueueTest {

	protected static IStoreClient<String, String> storeTopo;
	protected static String controllerID = "none";

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testDequeueForward() {
		TopoFilterQueue Topof2 = new TopoFilterQueue(storeTopo, controllerID);
		String testJson = new String("{\"src\":\"00:00:00:00:00:00:00:05\",\"operation\":\"Switch Removed\"}");
		assertEquals(Topof2.enqueueForward(testJson), true);
		Topof2.dequeueForward();
		assertEquals(TopoFilterQueue.filterQueue.size(), 0);
		TopoFilterQueue.myMap.clear();
		TopoFilterQueue.filterQueue.clear();
	}

	@Test
	public void testDequeueForward2() {
		TopoFilterQueue Topof2 = new TopoFilterQueue(storeTopo, controllerID);
		assertEquals(Topof2.dequeueForward(), false);
	}

	@Test
	public void testEnqueueForward() {
		TopoFilterQueue tf = new TopoFilterQueue(storeTopo, controllerID);
		assertEquals(tf.enqueueForward("cat"), true);
		assertEquals(TopoFilterQueue.myMap.get("d077f244def8a70e5ea758bd8352fcd8"), "cat");
		TopoFilterQueue.myMap.clear();
		TopoFilterQueue.filterQueue.clear();
	}

	@Test
	public void testEnqueueForward2() {
		TopoFilterQueue Topof2 = new TopoFilterQueue(storeTopo, controllerID);
		String testJson = new String("{\"src\":\"00:00:00:00:00:00:00:05\",\"operation\":\"Switch Removed\"}");
		assertEquals(Topof2.enqueueForward(testJson), true);
		assertEquals(TopoFilterQueue.myMap.get("f6816a638cbd1fcec9dcd88ebc2cfcb0"), testJson);
		assertEquals(TopoFilterQueue.myMap.get("f6816a638cbd1fcec9dcd88bc2cfcb0"), null);
		assertEquals(TopoFilterQueue.myMap.size(), 1);
		TopoFilterQueue.myMap.clear();
		TopoFilterQueue.filterQueue.clear();
	}

	@Test
	public void testReverse() {
		TopoFilterQueue Topof2 = new TopoFilterQueue(storeTopo, controllerID);
		String testJson = new String("{\"src\":\"00:00:00:00:00:00:00:05\",\"operation\":\"Switch Removed\"}");
		assertEquals(Topof2.enqueueReverse(testJson), true);
		Topof2.dequeueReverse();
		assertEquals(TopoFilterQueue.reverseFilterQueue.size(), 0);
		TopoFilterQueue.reverseFilterQueue.clear();
	}

	@Test
	public void testReverse2() {
		TopoFilterQueue Topof2 = new TopoFilterQueue(storeTopo, controllerID);
		String testJson = new String("cat{\"src\":\"00:00:00:00:00:00::\"Switch Removed\"}");
		assertEquals(Topof2.enqueueReverse(testJson), true);
		Topof2.dequeueReverse();
		assertEquals(TopoFilterQueue.reverseFilterQueue.size(), 0);
		TopoFilterQueue.reverseFilterQueue.clear();
	}

}
