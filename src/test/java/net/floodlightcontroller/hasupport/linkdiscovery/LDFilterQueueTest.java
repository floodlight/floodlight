package net.floodlightcontroller.hasupport.linkdiscovery;

import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class LDFilterQueueTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testEnqueueForward () {
		LDFilterQueue ldf = new LDFilterQueue();
		assertEquals(ldf.enqueueForward("cat"),true);
		assertEquals(LDFilterQueue.myMap.get("d077f244def8a70e5ea758bd8352fcd8"),"cat");
		LDFilterQueue.myMap.clear();
		LDFilterQueue.filterQueue.clear();
	}
	
	@Test
	public void testEnqueueForward2 () {
		LDFilterQueue ldf2 = new LDFilterQueue();
		String testJson = new String("{\"src\":\"00:00:00:00:00:00:00:05\",\"operation\":\"Switch Removed\"}");
		assertEquals(ldf2.enqueueForward(testJson),true);
		assertEquals(LDFilterQueue.myMap.get("f6816a638cbd1fcec9dcd88ebc2cfcb0"),testJson);
		assertEquals(LDFilterQueue.myMap.get("f6816a638cbd1fcec9dcd88bc2cfcb0"),null);
		assertEquals(LDFilterQueue.myMap.size(),1);
		LDFilterQueue.myMap.clear();
		LDFilterQueue.filterQueue.clear();
	}
	
	@Test
	public void testDequeueForward () {
		LDFilterQueue ldf2 = new LDFilterQueue();
		String testJson = new String("{\"src\":\"00:00:00:00:00:00:00:05\",\"operation\":\"Switch Removed\"}");
		assertEquals(ldf2.enqueueForward(testJson),true);
		ldf2.dequeueForward();
		assertEquals(LDFilterQueue.filterQueue.size(),0);
		LDFilterQueue.myMap.clear();
		LDFilterQueue.filterQueue.clear();
	}
	
	@Test
	public void testDequeueForward2 () {
		LDFilterQueue ldf2 = new LDFilterQueue();
		assertEquals(ldf2.dequeueForward(),false);
	}
	
	@Test
	public void testReverse () {
		LDFilterQueue ldf2 = new LDFilterQueue();
		String testJson = new String("{\"src\":\"00:00:00:00:00:00:00:05\",\"operation\":\"Switch Removed\"}");
		assertEquals(ldf2.enqueueReverse(testJson),true);
		ldf2.dequeueReverse();
		assertEquals(LDFilterQueue.reverseFilterQueue.size(),0);
		LDFilterQueue.reverseFilterQueue.clear();
	}
	
	@Test
	public void testReverse2 () {
		LDFilterQueue ldf2 = new LDFilterQueue();
		String testJson = new String("cat{\"src\":\"00:00:00:00:00:00::\"Switch Removed\"}");
		assertEquals(ldf2.enqueueReverse(testJson),true);
		ldf2.dequeueReverse();
		assertEquals(LDFilterQueue.reverseFilterQueue.size(),0);
		LDFilterQueue.reverseFilterQueue.clear();
	}

}