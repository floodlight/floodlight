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

import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.CharsetUtil;

public class ServerChannelInboundHandler extends SimpleChannelInboundHandler<ByteBuf> {

	private static final Logger logger = LoggerFactory.getLogger(ServerChannelInboundHandler.class);

	private final AsyncElection aelection;
	private final String controllerID;

	/**
	 * Possible outgoing server messages, replies.
	 */

	private final String ack = "ACK";
	private final String no = "NO";
	private final String lead = "LEADOK";
	private final String dc = "DONTCARE";
	private final String none = "none";

	private String r1 = new String();
	private String r2 = new String();
	private String r3 = new String();
	private StringTokenizer st = new StringTokenizer(r1);

	protected ServerChannelInboundHandler(AsyncElection ae, String controllerID) {
		aelection = ae;
		this.controllerID = controllerID;
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, ByteBuf message) throws Exception {
		ByteBuf rep = Unpooled.copiedBuffer(message);
		ByteBuf resp = Unpooled.copiedBuffer(processServerMessage(rep.toString(CharsetUtil.UTF_8)).getBytes());
		ctx.writeAndFlush(resp);
		message.clear();
	}

	/**
	 * A function which processes the incoming message and sends appropriate
	 * response. Only use the getters and setters provided.
	 * 
	 * @param mssg
	 * @return
	 */

	private String processServerMessage(String mssg) {
		/**
		 * Let's optimize the string comparision time, in order to get the best
		 * perf: 1) using first 1 chars of 'stg' to find out what message it
		 * was. 2) StringTokenizer to split the string at ' ' to get different
		 * parts of the message rather than using String.split because split
		 * uses a regex based match which is slower.
		 */
		char cmp = mssg.charAt(0);
		st = new StringTokenizer(mssg);
		r3 = none;
		r1 = st.nextToken();
		if (st.hasMoreTokens()) {
			r2 = st.nextToken();
		}
		if (st.hasMoreTokens()) {
			r3 = st.nextToken();
		}

		try {

			if (cmp == 'I') {

				// logger.info("[HAServer] Received IWon message: " +
				// mssg.toString());
				aelection.setTempLeader(r2);
				aelection.setTimeStamp(r3);
				return ack;

			} else if (cmp == 'L') {

				// logger.info("[HAServer] Received LEADER message: " +
				// mssg.toString());

				// logger.debug("[HAServer] Get tempLeader:
				// "+this.aelection.gettempLeader());

				if (aelection.gettempLeader().equals(r2) && aelection.getTimeStamp().equals(r3)) {
					return lead;
				} else {
					aelection.setTempLeader(none);
					aelection.setLeader(none);
					return no;
				}

			} else if (cmp == 'S') {

				// logger.info("[HAServer] Received SETLEAD message: " +
				// mssg.toString());

				// logger.info("[HAServer] Get Leader:
				// "+this.aelection.getLeader());

				if (!aelection.gettempLeader().equals(controllerID)) {
					if (aelection.gettempLeader().equals(r2) && aelection.getTimeStamp().equals(r3)) {
						aelection.setLeader(r2);
						aelection.setTempLeader(none);
						return ack;
					} else {
						aelection.setTempLeader(none);
						aelection.setLeader(none);
						return no;
					}
				} else {
					aelection.setTempLeader(none);
					aelection.setLeader(none);
					return no;
				}

			} else if (cmp == 'Y') {

				// logger.info("[HAServer] Received YOU? message: " +
				// mssg.toString());

				if (aelection.getLeader().equals(controllerID)) {
					return controllerID + " " + r2;
				} else {
					return no;
				}

			} else if (cmp == 'H') {

				// logger.info("[HAServer] Received HEARTBEAT message: " +
				// mssg.toString());

				if (aelection.getLeader().equals(r2)) {
					return ack + r3;
				} else {
					return no;
				}

			} else if (cmp == 'P') {

				// logger.info("[HAServer] Received PULSE message: " +
				// mssg.toString());
				return ack;

			} else if (cmp == 'B') {

				// logger.info("[HAServer] Received PUBLISH message");
				aelection.publishQueue();
				return ack;

			} else if (cmp == 'K') {

				// logger.info("[HAServer] Received SUBSCRIBE message");
				aelection.subscribeQueue(r2);
				return ack;

			} else if (cmp == 'm') {
				return ack;
			}
		} catch (StringIndexOutOfBoundsException si) {
			logger.debug("[HAServer] Possible buffer overflow!");
			si.printStackTrace();
			return dc;
		} catch (Exception e) {
			logger.debug("[HAServer] Error while processing message!");
			e.printStackTrace();
			return dc;
		}

		return dc;
	}

}
