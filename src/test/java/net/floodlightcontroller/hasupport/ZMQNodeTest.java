package net.floodlightcontroller.hasupport;

import static org.junit.Assert.*;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

@Ignore 
public class ZMQNodeTest {
	
	static String mockServerPort = new String("127.0.0.1:4242");
	static String mockTestServerPort = new String("127.0.0.1:5253");
	static String mockClientPort = new String("127.0.0.1:5252");
	static String nodeID		  = new String("1");
	static Thread ts;

	@BeforeClass
	public static void setUp() throws Exception {
//		ts = new Thread(new TestServer(mockTestServerPort));
//		ts.setDaemon(true);
//		ts.start();
	}
	
	@Test
	public void testPreStart() {
		NetworkNode znode = new NetworkNode(mockServerPort,mockClientPort,nodeID);
		assertEquals(true,znode.allServerList.contains("127.0.0.1:5252"));
		assertEquals(true,znode.allServerList.contains("127.0.0.1:5253"));
		assertEquals(true,znode.serverList.contains("127.0.0.1:5253"));
		assertEquals(true,znode.connectSet.contains("127.0.0.1:5253"));
		assertEquals(false,znode.serverList.contains("127.0.0.1:5252"));
		assertEquals(false,znode.connectSet.contains("127.0.0.1:5252"));
	}
	
	@Test
	public void testSendRecv1() {
		NetworkNode znode = new NetworkNode(mockServerPort,mockClientPort,nodeID);
		znode.connectClients();
		assertEquals(true,znode.socketDict.containsKey("127.0.0.1:5253"));
		znode.send("127.0.0.1:5253", "hi");
		String resp = znode.recv("127.0.0.1:5253");
		assertEquals(resp,"ACK");
		System.gc();
	}
	
	@Test
	public void testSendRecv2() {
		NetworkNode znode = new NetworkNode(mockServerPort,mockClientPort,nodeID);
		znode.connectClients();
		assertEquals(true,znode.socketDict.containsKey("127.0.0.1:5253"));
		znode.send("127.0.0.1:5253", "*$#(&$*#");
		String resp = znode.recv("127.0.0.1:5253");
		assertEquals(resp,"ACK");
	}
	
	@Test
	public void testConnectClients() {
		NetworkNode znode = new NetworkNode(mockServerPort,mockClientPort,nodeID);
		znode.connectClients();
		assertEquals(true,znode.socketDict.containsKey("127.0.0.1:5253"));
		assertEquals(true,znode.connectDict.containsKey("127.0.0.1:5253"));
	}
	
	
	@AfterClass
	public static void tearDown() throws Exception {
		// Testing expireOldConnections
		NetworkNode znode = new NetworkNode(mockServerPort,mockClientPort,nodeID);
		znode.connectClients();
		try {
			ts.interrupt();
		} catch (Exception e) {
			//e.printStackTrace();
		}
		znode.expireOldConnections();
		System.out.println(znode.socketDict.toString());
		System.out.println(znode.connectDict.toString());
		assertEquals(true,znode.socketDict.containsKey("127.0.0.1:5253"));
		assertEquals(true,znode.connectDict.containsKey("127.0.0.1:5253"));
	}

}
