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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * The HA Server 
 * This server class is instantiated by the AsyncElection, and it
 * connects to the back-end of the QueueDevice. This means that more than one of
 * these classes can be instantiated depending on the load. The server processes
 * the request and sends out a reply, through the queue device to the front-end
 * of the queue to which the clients connect to.
 *
 * @author Bhargav Srinivasan, Om Kale
 *
 */

public class HAServer implements Runnable {

	private static final Logger logger = LoggerFactory.getLogger(HAServer.class);
	private String serverPort;

	private final AsyncElection aelection;
	private final String controllerID;

	/**
	 * Decide the socket timeout value based on how fast you think the leader
	 * should respond and also how far apart the actual nodes are placed. If you
	 * are trying to communicate with servers far away, then anything upto 10s
	 * would be a good value.
	 */

	public final Integer socketTimeout = new Integer(500);

	/**
	 * Instantiate a HAServer object. The AsyncElection class will instantiate a
	 * server object, which will be provided with the port it should run on, the
	 * current state of the async election itself and the controllerID of this
	 * controller.
	 *
	 * @param serverPort
	 *            The port which it should run on.
	 * @param aelection
	 *            Current running object of class AsyncElection
	 * @param controllerID
	 *            Controller ID of this controller instance.
	 */

	public HAServer(String serverPort, AsyncElection ae, String controllerID) {
		this.serverPort = serverPort;
		aelection = ae;
		this.controllerID = controllerID;

	}

	@Override
	public void run() {
		Integer lastfour = Integer.valueOf(serverPort.substring(10));

		EventLoopGroup serverbossPool = new NioEventLoopGroup(1);
		EventLoopGroup serverworkerPool = new NioEventLoopGroup(16);

		ServerBootstrap sb = new ServerBootstrap();
		sb.group(serverbossPool, serverworkerPool).channel(NioServerSocketChannel.class)
				.localAddress(new InetSocketAddress("0.0.0.0", lastfour))
				.childHandler(new ServerChannelInitializer(aelection, controllerID));

		try {
			Channel ls = sb.bind().sync().channel();
			logger.info("Starting HAServer...");
			ls.closeFuture().sync();
		} catch (InterruptedException e) {
		} finally {
			serverbossPool.shutdownGracefully();
			serverworkerPool.shutdownGracefully();
		}

		return;
	}

}
