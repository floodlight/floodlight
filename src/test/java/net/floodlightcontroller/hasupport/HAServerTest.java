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

package net.floodlightcontroller.hasupport;

import static org.junit.Assert.assertEquals;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class HAServerTest {

	private static AsyncElection ae;
	private static Thread ts;
	private static TestClient tc;
	private static Thread aelt;
	private static String mockServerPort = "127.0.0.1:4242";
	private static String mockClientPort = "127.0.0.1:4243";
	private static String nodeID = "1";

	@BeforeClass
	public static void setUp() throws Exception {
		try {
			ae = new AsyncElection(mockServerPort, nodeID);
			aelt = new Thread(ae);
			ts = new Thread(new TestServer(mockClientPort, ae));
			ts.setDaemon(true);
			aelt.setDaemon(true);
			ts.start();
			aelt.start();
			tc = new TestClient(mockClientPort);
			Thread.sleep(500);
		} catch (Exception e) {
		}
	}

	@AfterClass
	public static void tearDown() throws Exception {
		try {
			aelt.interrupt();
			ts.interrupt();
		} catch (Exception e) {
		}
	}

	private final String no = "NO";
	private final String ack = "ACK";
	private final String none = "none";

	private final String dc = "DONTCARE";

	@Test
	public void testcheckForLeader() {
		ae.setTempLeader("1");
		ae.setLeader("1");
		String timestamp = String.valueOf(System.nanoTime());
		String resp = tc.send("YOU? " + timestamp);
		assertEquals(resp, nodeID + " " + timestamp);
		ae.setTempLeader(none);
		ae.setLeader(none);
	}

	@Test
	public void testcheckForLeader2() {
		String resp = tc.send("YOU? ");
		assertEquals(resp, no);
		ae.setTempLeader(none);
		ae.setLeader(none);
	}

	@Test
	public void testcheckForLeader3() {
		String timestamp = String.valueOf(System.nanoTime());
		String resp = tc.send("YOU? " + timestamp);
		assertEquals(resp, no);
		ae.setTempLeader(none);
		ae.setLeader(none);
	}

	@Test
	public void testHeartBeat() {
		ae.setTempLeader("2");
		ae.setLeader("2");
		String timestamp = String.valueOf(System.nanoTime());
		String resp = tc.send("HEARTBEAT 2 " + timestamp);
		assertEquals(resp, ack + timestamp);
		ae.setTempLeader(none);
		ae.setLeader(none);
	}

	@Test
	public void testHeartBeat2() {
		ae.setTempLeader("2");
		ae.setLeader("2");
		String resp = tc.send("HEARTBEAT 2 ");
		assertEquals(resp, ack + none);
		ae.setTempLeader(none);
		ae.setLeader(none);
	}

	@Test
	public void testRandom() {
		String recv = tc.send("0BK");
		assertEquals(recv, dc);
	}

	@Test
	public void testRandom2() {
		String recv = tc.send("@#$%");
		assertEquals(recv, dc);
	}

	@Test
	public void testRun() {
		String recv = tc.send("LOL 3");
		assertEquals(recv, no);
	}

	@Test
	public void testSetLeader() {
		String timestamp = String.valueOf(System.nanoTime());
		tc.send("IWON 2 " + timestamp);
		tc.send("LEADER 2 " + timestamp);
		tc.send("SETLEAD 2 " + timestamp);
		assertEquals(ae.getLeader(), "2");
		ae.setTempLeader(none);
		ae.setLeader(none);
	}

	@Test
	public void testSetLeaderIlleagal() {
		String timestamp = String.valueOf(System.nanoTime());
		tc.send("IWON 2 " + timestamp);
		tc.send("SETLEAD 2 " + timestamp);
		tc.send("LEADER 2 " + timestamp);
		assertEquals(ae.getLeader(), none);
		ae.setTempLeader(none);
		ae.setLeader(none);
	}

	@Test
	public void testSetLeaderIlleagal2() {
		String timestamp = String.valueOf(System.nanoTime());
		tc.send("IWON 2 " + timestamp);
		tc.send("LEADER 2 " + timestamp);
		tc.send("SETLEAD 3 " + timestamp);
		assertEquals(ae.getLeader(), none);
		ae.setTempLeader(none);
		ae.setLeader(none);
	}

	@Test
	public void testSetLeaderIlleagal3() {
		String timestamp = String.valueOf(System.nanoTime());
		tc.send("IWON 2 " + timestamp);
		timestamp = String.valueOf(System.nanoTime());
		tc.send("LEADER 2 " + timestamp);
		tc.send("SETLEAD 2 " + timestamp);
		assertEquals(ae.getLeader(), none);
		ae.setTempLeader(none);
		ae.setLeader(none);
	}

	@Test
	public void testSetTempLeader() {
		String timestamp = String.valueOf(System.nanoTime());
		tc.send("IWON 2 " + timestamp);
		tc.send("LEADER 2 " + timestamp);

		assertEquals(ae.gettempLeader(), "2");
		ae.setTempLeader(none);
		ae.setLeader(none);
	}

	@Test
	public void testZMQServer() {
		String recv = tc.send("PULSE");
		assertEquals(recv, ack);
	}

}
