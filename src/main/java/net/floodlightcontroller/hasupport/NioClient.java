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

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

/**
 * Doesn't hold socket objects, however, holds all general options, configs in
 * order to create the sockets.
 */

public class NioClient {

	private static final int READ_BUF_SIZE = 1024;
	private Integer sendTO;
	private Integer linger;
	private SocketChannel sc;

	/**
	 * Constructor should take all standard params required, like connection
	 * timeout, SO_LINGER etc.
	 */

	public NioClient(Integer sndTimeOut, Integer linger) {
		sendTO = sndTimeOut;
		this.linger = linger;

	}

	public SocketChannel connectClient(String host) {
		Integer port = Integer.valueOf(host.substring(10));
		String host2 = host.substring(0, 9);

		InetSocketAddress inet = new InetSocketAddress(host2, port);
		try {
			sc = SocketChannel.open(inet);
			sc.socket().setSoTimeout(sendTO);
			sc.socket().setTcpNoDelay(false);
			sc.socket().setSoLinger(false, linger);
			sc.socket().setReuseAddress(true);
			sc.socket().setPerformancePreferences(1, 2, 0);
			return sc;
		} catch (Exception e) {
			return null;
		}
	}

	public Boolean deleteConnection() {
		try {
			if (sc != null) {
				sc.close();
				sc.socket().close();
			}
			return Boolean.TRUE;
		} catch (Exception e) {
			return Boolean.FALSE;
		}

	}

	public SocketChannel getSocketChannel() {
		try {
			return sc;
		} catch (Exception e) {
			return null;
		}
	}

	public String recv() {
		try {
			ByteBuffer dst = ByteBuffer.allocate(READ_BUF_SIZE);
			sc.read(dst);
			return new String(dst.array()).trim();
		} catch (Exception e) {
			if (sc != null) {
				this.deleteConnection();
			}
			return "none";
		}
	}

	public Boolean send(String message) {
		if (message.equals(null)) {
			return Boolean.FALSE;
		}

		try {
			sc.write(ByteBuffer.wrap(message.getBytes(Charset.forName("UTF-8"))));
			return Boolean.TRUE;
		} catch (Exception e) {
			if (sc != null) {
				this.deleteConnection();
			}
			return Boolean.FALSE;
		}

	}

}
