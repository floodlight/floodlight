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
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sdnplatform.sync.IStoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TopoHAWorkerTest {

	private static final Logger logger = LoggerFactory.getLogger(TopoHAWorkerTest.class);
	protected static IStoreClient<String, String> storeTopo;
	protected static String controllerID = "none";
	private static TopoHAWorker topohaworker = new TopoHAWorker(storeTopo, controllerID);
	private static TopoFilterQueue filterQ = new TopoFilterQueue(storeTopo, controllerID);

	@BeforeClass
	public static void setUp() throws Exception {

	}

	@AfterClass
	public static void tearDown() throws Exception {
	}

	@Test
	public void testassembleUpdate() {
		TypeReference<HashMap<String, String>> typeRef = new TypeReference<HashMap<String, String>>() {
		};
		Set<String> resultSet = new HashSet<>();
		resultSet.add("operation");
		resultSet.add("src");
		List<String> jsonInString = new LinkedList<>();
		Map<String, String> jsonMap = new HashMap<>();
		ObjectMapper myObj = new ObjectMapper();
		TopoUtils parser = new TopoUtils();

		String preprocess = new String("[LDUpdate [operation=Switch Removed, src=00:00:00:00:00:00:00:05]]");

		if (preprocess.startsWith("[")) {
			preprocess = preprocess.substring(1, preprocess.length());
		}

		String chunk = new String(preprocess.toString());

		if (!preprocess.startsWith("]")) {
			jsonInString = parser.parseChunk(chunk);
			try {
				jsonMap = myObj.readValue(jsonInString.get(0).toString(), typeRef);
			} catch (JsonParseException e) {
				e.printStackTrace();
			} catch (JsonMappingException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		assertEquals(resultSet, jsonMap.keySet());
		logger.info("[Test1] JSON String: {} {}", new Object[] { jsonMap.keySet().toString(), resultSet.toString() });

		jsonInString = new LinkedList<>();
		jsonMap = new HashMap<>();
		resultSet = new HashSet<>();
		resultSet.add("operation");

		preprocess = new String("[LDUpdate [operation=Switch Removed src=00:00:00:00:00:00:00:05]]");

		if (preprocess.startsWith("[")) {
			preprocess = preprocess.substring(1, preprocess.length());
		}

		chunk = new String(preprocess.toString());

		if (!preprocess.startsWith("]")) {
			jsonInString = parser.parseChunk(chunk);
			try {
				jsonMap = myObj.readValue(jsonInString.get(0).toString(), typeRef);
			} catch (JsonParseException e) {
				e.printStackTrace();
			} catch (JsonMappingException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		assertEquals(resultSet, jsonMap.keySet());
		logger.info("[Test2] JSON String: {} {}", new Object[] { jsonMap.keySet().toString(), resultSet.toString() });

		jsonInString = new LinkedList<>();
		jsonMap = new HashMap<>();

		preprocess = new String("[]]");

		if (preprocess.startsWith("[")) {
			preprocess = preprocess.substring(1, preprocess.length());
		}

		chunk = new String(preprocess.toString());

		if (!preprocess.startsWith("]")) {
			jsonInString = parser.parseChunk(chunk);
			try {
				jsonMap = myObj.readValue(jsonInString.get(0).toString(), typeRef);
				fail("Equals");
			} catch (JsonParseException e) {
				e.printStackTrace();
			} catch (JsonMappingException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			logger.info("[Test3] Success, did not decode invalid input");
		}

		jsonInString = new LinkedList<>();
		jsonMap = new HashMap<>();
		resultSet = new HashSet<>();
		resultSet.add("operation");
		resultSet.add("src");

		preprocess = new String("[asda]");

		if (preprocess.startsWith("[")) {
			preprocess = preprocess.substring(1, preprocess.length());
		}

		chunk = new String(preprocess.toString());

		try {
			if (!preprocess.startsWith("]")) {
				jsonInString = parser.parseChunk(chunk);
				jsonMap = myObj.readValue(jsonInString.get(0).toString(), typeRef);
			}
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (IndexOutOfBoundsException e) {
			logger.info("[Test4] Successfully caught IndexOutOfBounds Exception");
		}

		if (!jsonMap.keySet().isEmpty()) {
			fail("Result should be empty");
		}

		jsonInString = new LinkedList<>();
		jsonMap = new HashMap<>();
		resultSet = new HashSet<>();
		resultSet.add("operation");
		resultSet.add("src");

		preprocess = new String("");

		if (preprocess.startsWith("[")) {
			preprocess = preprocess.substring(1, preprocess.length());
		}

		chunk = new String(preprocess.toString());

		try {
			if (!preprocess.startsWith("]")) {
				jsonInString = parser.parseChunk(chunk);
				jsonMap = myObj.readValue(jsonInString.get(0).toString(), typeRef);
			}
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (IndexOutOfBoundsException e) {
			logger.info("[Test5] Successfully caught IndexOutOfBounds Exception");
		}

		if (!jsonMap.keySet().isEmpty()) {
			fail("Result should be empty");
		}

		jsonInString = new LinkedList<>();
		jsonMap = new HashMap<>();
		resultSet = new HashSet<>();
		resultSet.add("operation");
		resultSet.add("src");

		preprocess = null;

		try {
			jsonInString = parser.parseChunk(chunk);
			jsonMap = myObj.readValue(jsonInString.get(0).toString(), typeRef);
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (IndexOutOfBoundsException e) {
			logger.info("[Test6] Successfully caught IndexOutOfBounds Exception");
		} catch (NullPointerException ne) {
			ne.printStackTrace();
		}

		if (!jsonMap.keySet().isEmpty()) {
			fail("Result should be empty");
		}

		jsonInString = new LinkedList<>();
		jsonMap = new HashMap<>();
		resultSet = new HashSet<>();
		resultSet.add("operation");
		resultSet.add("src");

		preprocess = new String("[]][");

		if (preprocess.startsWith("[")) {
			preprocess = preprocess.substring(1, preprocess.length());
		}

		chunk = new String(preprocess.toString());

		try {
			if (!preprocess.startsWith("]")) {
				jsonInString = parser.parseChunk(chunk);
				jsonMap = myObj.readValue(jsonInString.get(0).toString(), typeRef);
			}
		} catch (JsonParseException e) {
			e.printStackTrace();
		} catch (JsonMappingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		if (!jsonMap.keySet().isEmpty()) {
			fail("Result should be empty");
		}

		logger.info("[Test7] Success, Result set is empty.");

	}

	@Test
	public void testPublishHook() {
		List<String> updates = new LinkedList<>();
		TopoUtils parser = new TopoUtils();
		String preprocess = new String("[LDUpdate [operation=Switch Removed, src=00:00:00:00:00:00:00:05]]");

		if (preprocess.startsWith("[")) {
			preprocess = preprocess.substring(1, preprocess.length());
		}

		String chunk = new String(preprocess.toString());

		if (!preprocess.startsWith("]")) {
			updates = parser.parseChunk(chunk);
		} else {
			fail("[Test Publish] Could not assemble updates");
		}

		try {
			for (String update : updates) {
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
		try {
			topohaworker.synTopoUList.add("LDUpdate [operation=Switch Removed, src=00:00:00:00:00:00:00:05]");
			List<String> updates = topohaworker.assembleUpdate();
			for (String update : updates) {
				filterQ.enqueueReverse(update);
			}
			filterQ.dequeueReverse();

		} catch (Exception e) {
			e.printStackTrace();
			fail("[Test Subscribe] Failed, exception occured");

		}

	}

}
