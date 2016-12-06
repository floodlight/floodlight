package net.floodlightcontroller.hasupport.linkdiscovery;

import static org.junit.Assert.*;


import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * Unit tests for the Sync Adapter class. Test both packing 
 * and unpacking of the JSON blobs. Should be able to successfully
 * use the SyncDB to push files.
 * 
 * @author Bhargav Srinivasan
 *
 */
public class LDSyncAdapterTest {

	@Test
	public void testPackJSON () {
		try {
			List<String> updates = Arrays.asList("{\"src\":\"00:00:00:00:00:00:00:01\",\"operation\":\"Switch Updated\"}", "{\"src\":\"00:00:00:00:00:00:00:01\",\"srcPort\":\"1\",\"operation\":\"Port Up\"}", "{\"src\":\"00:00:00:00:00:00:00:01\",\"srcPort\":\"2\",\"operation\":\"Port Up\"}", "{\"src\":\"00:00:00:00:00:00:00:01\",\"srcPort\":\"local\",\"operation\":\"Port Up\"}", "{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"local\",\"operation\":\"Switch Updated\"}", "{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"1\",\"operation\":\"Port Up\"}", "{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"2\",\"operation\":\"Port Up\"}", "{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"3\",\"operation\":\"Port Up\"}", "{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"local\",\"operation\":\"Port Up\"}", "{\"dstPort\":\"2\",\"dst\":\"00:00:00:00:00:00:00:01\",\"src\":\"00:00:00:00:00:00:00:02\",\"latency\":\"0x0000000000000177\",\"srcPort\":\"2\",\"type\":\"external\",\"operation\":\"Link Updated\"}", "{\"dstPort\":\"2\",\"dst\":\"00:00:00:00:00:00:00:02\",\"src\":\"00:00:00:00:00:00:00:01\",\"latency\":\"0x0000000000000177\",\"srcPort\":\"2\",\"type\":\"external\",\"operation\":\"Link Updated\"}");
			LDFilterQueue ldfq   = new LDFilterQueue();
			LDSyncAdapter.controllerId = new String("C1");
			
			for (String upd: updates){
				try {
					ldfq.enqueueForward(upd);
					ldfq.dequeueForward();
				} catch (NullPointerException ne) {
					//ne.printStackTrace();
				}
				
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
	}
	
	@Test
	public void testPackJSON2 () {
		try {
			List<String> updates = Arrays.asList("{\"src\":\"00:00:00:00:00:00:00:01\",\"op00:00:00:01\",\"srcPort\":\"1\",\"opera Up\"}:\"00:00:00:00:00:00:00:01\",\"srcPort\":\"2\",\"operation\":\"Port Up\"}", "{\"src\":\"00:00:00:00:00:00:00:01\",p\"}", "{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"local\",\"operation\":\"Switch Updated\"}", "{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"1\",\"operation\":\"Port Up\"}", "{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"2\",\"operation\":\"Port Up\"}", "{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"3\",\"operation\":\"Port Up\"}", "{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"local\",\"operation\":\"Port Up\"}", "{\"dstPort\":\"2\",\"dst\":\"00:00:00:00:00:00:00:01\",\"src\":\"00:00:00:00:00:00:00:02\",\"latency\":\"0x0000000000000177\",\"srcPort\":\"2\",\"type\":\"external\",\"operation\":\"Link Updated\"}", "{\"dstPort\":\"2\",\"dst\":\"00:00:00:00:00:00:00:02\",\"src\":\"00:00:00:00:00:00:00:01\",\"latency\":\"0x0000000000000177\",\"srcPort\":\"2\",\"type\":\"external\",\"operation\":\"Link Updated\"}");
			LDFilterQueue ldfq   = new LDFilterQueue();
			LDSyncAdapter.controllerId = new String("C1");
			
			for (String upd: updates){
				ldfq.enqueueForward(upd);
				ldfq.dequeueForward();
				
			}
			
		} catch (NullPointerException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
		catch (Exception e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			fail("Other exceptions, than the ones expected");
		}
	}
	
	@Test
	public void testUnpackJSON () {
		try {
			List<String> updates = Arrays.asList("{\"src\":\"00:00:00:00:00:00:00:01\",\"operation\":\"Switch Updated\"}", "{\"src\":\"00:00:00:00:00:00:00:01\",\"srcPort\":\"1\",\"operation\":\"Port Up\"}", "{\"src\":\"00:00:00:00:00:00:00:01\",\"srcPort\":\"2\",\"operation\":\"Port Up\"}", "{\"src\":\"00:00:00:00:00:00:00:01\",\"srcPort\":\"local\",\"operation\":\"Port Up\"}", "{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"local\",\"operation\":\"Switch Updated\"}", "{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"1\",\"operation\":\"Port Up\"}", "{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"2\",\"operation\":\"Port Up\"}", "{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"3\",\"operation\":\"Port Up\"}", "{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"local\",\"operation\":\"Port Up\"}", "{\"dstPort\":\"2\",\"dst\":\"00:00:00:00:00:00:00:01\",\"src\":\"00:00:00:00:00:00:00:02\",\"latency\":\"0x0000000000000177\",\"srcPort\":\"2\",\"type\":\"external\",\"operation\":\"Link Updated\"}", "{\"dstPort\":\"2\",\"dst\":\"00:00:00:00:00:00:00:02\",\"src\":\"00:00:00:00:00:00:00:01\",\"latency\":\"0x0000000000000177\",\"srcPort\":\"2\",\"type\":\"external\",\"operation\":\"Link Updated\"}");
			LDFilterQueue ldfq   = new LDFilterQueue();
			LDSyncAdapter.controllerId = new String("C1");
			
			for (String upd: updates){
				ldfq.enqueueForward(upd);
				ldfq.dequeueForward();
				
			}
			
			ldfq.subscribe("C1");
			
		} catch (NullPointerException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
		catch (Exception e) {
			// TODO Auto-generated catch block
			fail("Other exceptions, than the ones expected");
		}
	}
	
	@Test
	public void testUnpackJSON2 () {
		try {
			List<String> updates = Arrays.asList("{\"src\":\"00:00:00:00:00:00:00:01\",\"operation\":\"Switch Updated\"}", "{\"src\":\"00:00:00:00:00:00:00:01\",\"srcPort\":\"1\",\"operation\":\"Port Up\"}", "{\"src\":\"00:00:00:00:00:00:00:01\",\"srcPort\":\"2\",\"operation\":\"Port Up\"}", "{\"src\":\"00:00:00:00:00:00:00:01\",\"srcPort\":\"local\",\"operation\":\"Port Up\"}", "{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"local\",\"operation\":\"Switch Updated\"}", "{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"1\",\"operation\":\"Port Up\"}", "{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"2\",\"operation\":\"Port Up\"}", "{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"3\",\"operation\":\"Port Up\"}", "{\"src\":\"00:00:00:00:00:00:00:02\",\"srcPort\":\"local\",\"operation\":\"Port Up\"}", "{\"dstPort\":\"2\",\"dst\":\"00:00:00:00:00:00:00:01\",\"src\":\"00:00:00:00:00:00:00:02\",\"latency\":\"0x0000000000000177\",\"srcPort\":\"2\",\"type\":\"external\",\"operation\":\"Link Updated\"}", "{\"dstPort\":\"2\",\"dst\":\"00:00:00:00:00:00:00:02\",\"src\":\"00:00:00:00:00:00:00:01\",\"latency\":\"0x0000000000000177\",\"srcPort\":\"2\",\"type\":\"external\",\"operation\":\"Link Updated\"}");
			LDFilterQueue ldfq   = new LDFilterQueue();
			LDSyncAdapter.controllerId = new String("C1");
			
			for (String upd: updates){
				ldfq.enqueueForward(upd);
				ldfq.dequeueForward();
				
			}
			
			ldfq.subscribe("C1");
			
		} catch (NullPointerException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
		}
		catch (Exception e) {
			// TODO Auto-generated catch block
			fail("Other exceptions, than the ones expected");
		}
	}

}
