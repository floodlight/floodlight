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

import org.junit.Ignore;

@Ignore
public class TestClient {

	private String mockClientPort = new String();
	private NioClient nclient = new NioClient(500, 0);

	public TestClient(String clientPort) {
		mockClientPort = clientPort;
	}

	public String send(String message) {
		try {
			nclient.connectClient(mockClientPort);
			nclient.send(message);
			String resp = nclient.recv();
			resp.trim();
			nclient.deleteConnection();
			return resp;
		} catch (Exception e) {
			nclient.deleteConnection();
		}
		return new String("none");
	}

}
