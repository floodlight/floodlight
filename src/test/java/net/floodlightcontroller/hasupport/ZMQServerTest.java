package net.floodlightcontroller.hasupport;

import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.reflect.Field;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.zeromq.*;

public class ZMQServerTest {
	
	static AsyncElection ae;
	static TestClient    tc;
	static QueueDevice   qu;
	static Thread qD;
	static Thread servThread;
	static Thread ael;
	static String mockServerPort = new String("127.0.0.1:4242");
	static String mockClientPort = new String("127.0.0.1:5252");
	static String nodeID		  = new String("1");

	@BeforeClass
	public static void setUp() throws Exception {
		setSysPath();
		ae = new AsyncElection(mockServerPort, nodeID);
        tc = new TestClient(mockClientPort);
        qD = new Thread(new Runnable() {
        	public void run() {
        		startQueue();
        	}
        });
        qD.start();
        ZMQServer zserver = new ZMQServer(mockServerPort,ae,nodeID);
        servThread = new Thread(zserver);
		servThread.start();
	}
	private final String no   = new String("NO");
	private final String ack  = new String("ACK");
	private final String none = new String("none");
	private final String dc   = new String("DONTCARE");
	
	@Test
	public void testSetTempLeader() {
		String timestamp = String.valueOf(System.nanoTime()); 
		tc.send("IWON 2 "+timestamp);
		tc.send("LEADER 2 "+timestamp);
		
		assertEquals(ae.gettempLeader(),"2");
		ae.setTempLeader(none);
		ae.setLeader(none);
	}
	
	@Test
	public void testSetLeader() {
		String timestamp = String.valueOf(System.nanoTime()); 
		tc.send("IWON 2 "+timestamp);
		tc.send("LEADER 2 "+timestamp);
		tc.send("SETLEAD 2 "+timestamp);
		assertEquals(ae.getLeader(),"2");
		ae.setTempLeader(none);
		ae.setLeader(none);
	}
	
	@Test
	public void testZMQServer() {
		//Check PULSE feature
		String recv = tc.send("PULSE");
		assertEquals(recv,ack);
	}
	
	@Test
	public void testRun() {
		String recv = tc.send("LOL 3");
		assertEquals(recv,no);
	}
	
	@Test
	public void testRandom() {
		String recv = tc.send("0BK");
		assertEquals(recv,dc);
	}
	
	@Test
	public void testRandom2() {
		String recv = tc.send("@#$%");
		assertEquals(recv,dc);
	}
	
	
	@Test
	public void testSetLeaderIlleagal() {
		// Check integrity of 3PC ordering
		String timestamp = String.valueOf(System.nanoTime()); 
		tc.send("IWON 2 "+timestamp);
		tc.send("SETLEAD 2 "+timestamp);
		tc.send("LEADER 2 "+timestamp);
		assertEquals(ae.getLeader(),none);
		ae.setTempLeader(none);
		ae.setLeader(none);
	}
	
	@Test
	public void testSetLeaderIlleagal2() {
		// Check integrity of 3PC value
		String timestamp = String.valueOf(System.nanoTime()); 
		tc.send("IWON 2 "+timestamp);
		tc.send("LEADER 2 "+timestamp);
		tc.send("SETLEAD 3 "+timestamp);
		assertEquals(ae.getLeader(),none);
		ae.setTempLeader(none);
		ae.setLeader(none);
	}
	
	@Test
	public void testSetLeaderIlleagal3() {
		// Check timestamp feature
		String timestamp = String.valueOf(System.nanoTime()); 
		tc.send("IWON 2 "+timestamp);
		timestamp = String.valueOf(System.nanoTime());
		tc.send("LEADER 2 "+timestamp);
		tc.send("SETLEAD 2 "+timestamp);
		assertEquals(ae.getLeader(),none);
		ae.setTempLeader(none);
		ae.setLeader(none);
	}
	
	@Test
	public void testcheckForLeader() {
		// Check feature
		ae.setTempLeader("1");
		ae.setLeader("1");
		String timestamp = String.valueOf(System.nanoTime()); 
		String resp = tc.send("YOU? "+timestamp);
		assertEquals(resp,nodeID+" "+timestamp);
		ae.setTempLeader(none);
		ae.setLeader(none);
	}
	
	@Test
	public void testcheckForLeader2() {
		// Check timestamp feature
		String resp = tc.send("YOU? ");
		assertEquals(resp,no);
		ae.setTempLeader(none);
		ae.setLeader(none);
	}
	
	@Test
	public void testcheckForLeader3() {
		// Check feature: NO
		String timestamp = String.valueOf(System.nanoTime()); 
		String resp = tc.send("YOU? "+timestamp);
		assertEquals(resp,no);
		ae.setTempLeader(none);
		ae.setLeader(none);
	}
	
	@Test
	public void testHeartBeat() {
		// Check feature: basic
		ae.setTempLeader("2");
		ae.setLeader("2");
		String timestamp = String.valueOf(System.nanoTime()); 
		String resp = tc.send("HEARTBEAT 2 "+timestamp);
		assertEquals(resp,ack +timestamp);
		ae.setTempLeader(none);
		ae.setLeader(none);
	}
	
	@Test
	public void testHeartBeat2() {
		// Check feature: timestamp
		ae.setTempLeader("2");
		ae.setLeader("2");
		String resp = tc.send("HEARTBEAT 2 ");
		assertEquals(resp,dc);
		ae.setTempLeader(none);
		ae.setLeader(none);
	}
	
	
	@AfterClass
	public static void tearDown() throws Exception {
		try {
			servThread.interrupt();
			qD.interrupt();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void setSysPath(){
		System.setProperty("java.library.path", "lib/");
		System.setProperty("java.class.path", "lib/zmq.jar");
		Field sysPathsField;
		try {
			sysPathsField = ClassLoader.class.getDeclaredField("sys_paths");
			sysPathsField.setAccessible(true);
		    sysPathsField.set(null, null);
		} catch (NoSuchFieldException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (SecurityException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return;
	}
	
	public static void startQueue() {
		
		try{
			/**
			 * Number of I/O threads assigned to the queue device.
			 */
			ZMQ.Context zmqcontext = ZMQ.context(10);
			
			/** 
			 * Connection facing the outside, where other nodes can connect 
			 * to this node. (frontend)
			 */

			ZMQ.Socket clientSide = zmqcontext.socket(ZMQ.ROUTER);
			clientSide.bind("tcp://0.0.0.0:5252");
			
			
			/**
			 * The backend of the load balancing queue and the server 
			 * which handles all the incoming requests from the frontend.
			 * (backend)
			 */
			ZMQ.Socket serverSide = zmqcontext.socket(ZMQ.DEALER);
			serverSide.bind("tcp://0.0.0.0:4242");
			
			System.out.println("Starting ZMQueue device...");
			
			/**
			 * This is an infinite loop to run the QueueDevice!
			 */
			ZMQQueue queue = new ZMQQueue(zmqcontext,clientSide,serverSide);
			queue.run();
			
			queue.close();
			clientSide.close();
			serverSide.close();
			zmqcontext.term();
			
		} catch (ZMQException ze){		
			ze.printStackTrace();	
		} catch (IOException ie){
			ie.printStackTrace();
		} catch (Exception e){
			e.printStackTrace();
		}
		
	}

}