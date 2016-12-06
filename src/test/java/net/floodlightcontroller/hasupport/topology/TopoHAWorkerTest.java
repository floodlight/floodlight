package net.floodlightcontroller.hasupport.topology;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;


import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TopoHAWorkerTest {
	
	protected static Logger logger = LoggerFactory.getLogger(TopoHAWorkerTest.class);
	private static final TopoHAWorker topohaworker = new TopoHAWorker();
	private static final TopoFilterQueue filterQ = new TopoFilterQueue();
	
	@BeforeClass
	public static void setUp() throws Exception {
		
	}

	@AfterClass
	public static void tearDown() throws Exception {
	}

	@Test
	public void testassembleUpdate() {
		// TODO Auto-generated method stub
		TypeReference<HashMap<String, String>> typeRef = new TypeReference<HashMap<String,String>>() {};
		Set<String> resultSet = new HashSet<String>();
		resultSet.add("operation");
		resultSet.add("src");
		List<String> jsonInString = new LinkedList<String>();
		HashMap<String, String> jsonMap = new HashMap<String, String>();
	    ObjectMapper myObj = new ObjectMapper();
		TopoUtils parser = new TopoUtils();
		
		String preprocess = new String ("[LDUpdate [operation=Switch Removed, src=00:00:00:00:00:00:00:05]]");
		// Flatten the updates and strip off leading [
		
		if(preprocess.startsWith("[")){
			preprocess = preprocess.substring(1, preprocess.length());
		}
		
		String chunk = new String(preprocess.toString());
		
		if(! preprocess.startsWith("]") ) {
			jsonInString = parser.parseChunk(chunk);
			try {
				jsonMap = myObj.readValue(jsonInString.get(0).toString(), typeRef);
			} catch (JsonParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JsonMappingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		assertEquals(resultSet,jsonMap.keySet());
		logger.info("[Test1] JSON String: {} {}", new Object[] {jsonMap.keySet().toString(), resultSet.toString()});
		
		jsonInString = new LinkedList<String>();
		jsonMap = new HashMap<String,String>();
	    resultSet = new HashSet<String>();
		resultSet.add("operation");
		
		preprocess = new String ("[LDUpdate [operation=Switch Removed src=00:00:00:00:00:00:00:05]]");
		// Flatten the updates and strip off leading [
		
		if(preprocess.startsWith("[")){
			preprocess = preprocess.substring(1, preprocess.length());
		}
		
		chunk = new String(preprocess.toString());
		
		if(! preprocess.startsWith("]") ) {
			jsonInString = parser.parseChunk(chunk);
			try {
				jsonMap = myObj.readValue(jsonInString.get(0).toString(), typeRef);
			} catch (JsonParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JsonMappingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		assertEquals(resultSet,jsonMap.keySet());
		logger.info("[Test2] JSON String: {} {}", new Object[] {jsonMap.keySet().toString(), resultSet.toString()});
		
		jsonInString = new LinkedList<String>();
		jsonMap = new HashMap<String,String>();
		
		preprocess = new String ("[]]");
		// Flatten the updates and strip off leading [
		
		if(preprocess.startsWith("[")){
			preprocess = preprocess.substring(1, preprocess.length());
		}
		
		chunk = new String(preprocess.toString());
		
		if(! preprocess.startsWith("]") ) {
			jsonInString = parser.parseChunk(chunk);
			try {
				jsonMap = myObj.readValue(jsonInString.get(0).toString(), typeRef);
				fail("Equals");
			} catch (JsonParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JsonMappingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			logger.info("[Test3] Success, did not decode invalid input");
		}
		
		
		jsonInString = new LinkedList<String>();
		jsonMap = new HashMap<String,String>();
	    resultSet = new HashSet<String>();
		resultSet.add("operation");
		resultSet.add("src");
		
		preprocess = new String ("[asda]");
		// Flatten the updates and strip off leading [
		
		if(preprocess.startsWith("[")){
			preprocess = preprocess.substring(1, preprocess.length());
		}
		
	   chunk = new String(preprocess.toString());
		
		try {
			if(! preprocess.startsWith("]") ) {
				jsonInString = parser.parseChunk(chunk);
				jsonMap = myObj.readValue(jsonInString.get(0).toString(), typeRef);
			}
		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IndexOutOfBoundsException e){
			logger.info("[Test4] Successfully caught IndexOutOfBounds Exception");
		}
		
		if(! jsonMap.keySet().isEmpty() ) {
			fail("Result should be empty");
		}
		
		jsonInString = new LinkedList<String>();
		jsonMap = new HashMap<String,String>();
	    resultSet = new HashSet<String>();
		resultSet.add("operation");
		resultSet.add("src");
		
		preprocess = new String ("");
		// Flatten the updates and strip off leading [
		
		if(preprocess.startsWith("[")){
			preprocess = preprocess.substring(1, preprocess.length());
		}
		
	   chunk = new String(preprocess.toString());
		
		try {
			if(! preprocess.startsWith("]") ) {
				jsonInString = parser.parseChunk(chunk);
				jsonMap = myObj.readValue(jsonInString.get(0).toString(), typeRef);
			}
		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IndexOutOfBoundsException e){
			logger.info("[Test5] Successfully caught IndexOutOfBounds Exception");
		}
		
		if(! jsonMap.keySet().isEmpty() ) {
			fail("Result should be empty");
		}
		
		jsonInString = new LinkedList<String>();
		jsonMap = new HashMap<String,String>();
	    resultSet = new HashSet<String>();
		resultSet.add("operation");
		resultSet.add("src");
		
		preprocess = null;
		// Flatten the updates and strip off leading [
		
		try {
			jsonInString = parser.parseChunk(chunk);
			jsonMap = myObj.readValue(jsonInString.get(0).toString(), typeRef);
		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IndexOutOfBoundsException e) {
			logger.info("[Test6] Successfully caught IndexOutOfBounds Exception");
		} catch (NullPointerException ne) {
			ne.printStackTrace();
		}
		
		if(! jsonMap.keySet().isEmpty() ) {
			fail("Result should be empty");
		}
		
		jsonInString = new LinkedList<String>();
		jsonMap = new HashMap<String,String>();
	    resultSet = new HashSet<String>();
		resultSet.add("operation");
		resultSet.add("src");
		
		preprocess = new String ("[]][");
		// Flatten the updates and strip off leading [
		
		if(preprocess.startsWith("[")){
			preprocess = preprocess.substring(1, preprocess.length());
		}
		
	   chunk = new String(preprocess.toString());
		
		try {
			if(! preprocess.startsWith("]") ) {
				jsonInString = parser.parseChunk(chunk);
				jsonMap = myObj.readValue(jsonInString.get(0).toString(), typeRef);
			}
		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if(! jsonMap.keySet().isEmpty() ) {
			fail("Result should be empty");
		}
		
		logger.info("[Test7] Success, Result set is empty.");
		
	}
	
	@Test
	public void testPublishHook() {
		List<String> updates = new LinkedList<String>();
		TopoUtils parser = new TopoUtils();
		String preprocess = new String ("[LDUpdate [operation=Switch Removed, src=00:00:00:00:00:00:00:05]]");
		
		// Flatten the updates and strip off leading [	
		if(preprocess.startsWith("[")){
			preprocess = preprocess.substring(1, preprocess.length());
		}
		
		String chunk = new String(preprocess.toString());
		
		if(! preprocess.startsWith("]") ) {
			updates = parser.parseChunk(chunk);
		} else {
			fail("[Test Publish] Could not assemble updates");
		}
		
		try { 
			for( String update: updates) {
				filterQ.enqueueForward(update);
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail("[Test Publish] Exception!");
		}
		
		logger.info("[Test Publish 1] Success, updates were sent to the queue.");
		TopoFilterQueue.filterQueue.clear();
		
		
		try {
			topohaworker.synTopoUList.add("");
			topohaworker.publishHook();
		} catch (Exception e) {
			e.printStackTrace();
			fail("[Test Publish] Exception!");
		}
		
		TopoFilterQueue.filterQueue.clear();
		TopoFilterQueue.myMap.clear();
		topohaworker.synTopoUList.clear();
		
		logger.info("[Test Publish 2] Success, Published blank update string.");
		
	}
	
	
	@Test
	public void testSubscribeHook() {
		try{
			topohaworker.synTopoUList.add("LDUpdate [operation=Switch Removed, src=00:00:00:00:00:00:00:05]");
			List<String> updates = topohaworker.assembleUpdate();
			for(String update: updates){
				filterQ.enqueueReverse(update);
			}		
			filterQ.dequeueReverse();
			
		} catch (Exception e){
			e.printStackTrace();
			fail("[Test Subscribe] Failed, exception occured");
			
		}
		
	}
	
}
