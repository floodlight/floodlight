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

public class NetworkNodeTest {

	private static AsyncElection ae;
	private static String mockServerPort = "127.0.0.1:4242";
	private static String mockTestServerPort = "127.0.0.1:4243";
	private static String mockClientPort = "127.0.0.1:4242";
	private static String nodeID = "1";
	private static Thread ts;
	private static Thread aelt;

	@BeforeClass
	public static void setUp() throws Exception {
		try {
			ae = new AsyncElection(mockServerPort, nodeID);
			aelt = new Thread(ae);
			ts = new Thread(new TestServer(mockTestServerPort, ae));
			ts.setDaemon(true);
			aelt.setDaemon(true);
			ts.start();
			aelt.start();
			Thread.sleep(500);
		} catch (Exception e) {
		}
	}

	@AfterClass
	public static void tearDown() throws Exception {
		NetworkNode znode = new NetworkNode(mockServerPort, nodeID);
		znode.connectClients();
		try {
			aelt.interrupt();
			ts.interrupt();
		} catch (Exception e) {
		}
		znode.expireOldConnections();
		System.out.println(znode.getSocketDict().toString());
		System.out.println(znode.getConnectDict().toString());
		//assertEquals(true, znode.getSocketDict().containsKey("127.0.0.1:4243"));
		//assertEquals(true, znode.getConnectDict().containsKey("127.0.0.1:4243"));
		System.gc();
	}

	@Test
	public void testConnectClients() {
		NetworkNode znode = new NetworkNode(mockServerPort, nodeID);
		znode.connectClients();
		assertEquals(true, znode.getSocketDict().containsKey("127.0.0.1:4243"));
		assertEquals(true, znode.getConnectDict().containsKey("127.0.0.1:4243"));
		assertEquals(false, znode.getSocketDict().containsKey("127.0.0.1:4242"));
		assertEquals(false, znode.getConnectDict().containsKey("127.0.0.1:4242"));
	}

	@Test
	public void testPreStart() {
		NetworkNode znode = new NetworkNode(mockServerPort, nodeID);
		assertEquals(true, znode.getAllServerList().contains("127.0.0.1:4242"));
		assertEquals(true, znode.getAllServerList().contains("127.0.0.1:4243"));
		assertEquals(true, znode.getServerList().contains("127.0.0.1:4243"));
		assertEquals(true, znode.getConnectSet().contains("127.0.0.1:4243"));
		assertEquals(false, znode.getServerList().contains("127.0.0.1:4242"));
		assertEquals(false, znode.getConnectSet().contains("127.0.0.1:4242"));
	}

	@Test
	public void testSendRecv1() {
		NetworkNode znode = new NetworkNode(mockServerPort, nodeID);
		znode.connectClients();
		assertEquals(true, znode.getSocketDict().containsKey("127.0.0.1:4243"));
		znode.send("127.0.0.1:4243", "mhi#QREF$$@");
		String resp = znode.recv("127.0.0.1:4243");
		assertEquals(resp, "ACK");
		System.gc();
	}

	@Test
	public void testSendRecv2() {
		NetworkNode znode = new NetworkNode(mockServerPort, nodeID);
		znode.connectClients();
		assertEquals(true, znode.getSocketDict().containsKey("127.0.0.1:4243"));
		znode.send("127.0.0.1:4243", "*$#(&$*#");
		String resp = znode.recv("127.0.0.1:4243");
		assertEquals(resp, "DONTCARE");
		System.gc();
	}

	@Test
	public void testSendRecv3() {
		NetworkNode znode = new NetworkNode(mockServerPort, nodeID);
		znode.connectClients();
		assertEquals(true, znode.getSocketDict().containsKey("127.0.0.1:4243"));
		znode.send("127.0.0.1:4243", "PULSE");
		String resp = znode.recv("127.0.0.1:4243");
		assertEquals(resp, "ACK");
		System.gc();
	}

}
