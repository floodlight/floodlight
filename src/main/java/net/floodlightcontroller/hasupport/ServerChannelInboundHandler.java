package net.floodlightcontroller.hasupport;

import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.CharsetUtil;

public class ServerChannelInboundHandler extends SimpleChannelInboundHandler<ByteBuf>{
	
	private static final Logger logger = LoggerFactory.getLogger(ServerChannelInboundHandler.class);
	
	private final AsyncElection aelection;
	private final String controllerID;
	
	/**
	 * Possible outgoing server messages, replies.
	 */
	
	private final String ack      = "ACK";
	private final String no       = "NO";
	private final String lead     = "LEADOK";
	private final String dc       = "DONTCARE";
	private final String none     = "none";
	
	private String r1 = new String();
	private String r2 = new String();
	private String r3 = new String();
	private StringTokenizer st = new StringTokenizer(r1);
	
	protected ServerChannelInboundHandler(AsyncElection ae, String controllerID) {
		this.aelection = ae;
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
	 * A function which processes the incoming message and sends appropriate response.
	 * Only use the getters and setters provided.
	 * @param mssg
	 * @return
	 */

	private String processServerMessage(String mssg) {
		// Let's optimize the string comparision time, in order to 
		// get the best perf: 
		// 1) using first 1 chars of 'stg' to find out what
		// message it was.
		// 2) StringTokenizer to split the string at ' ' to get 
		// different parts of the message rather than using String.split
		// because split uses a regex based match which is slower.
		
		char cmp = mssg.charAt(0);
		st = new StringTokenizer(mssg);
		r3=none;
		r1 = st.nextToken();
		if (st.hasMoreTokens()) {
			r2 = st.nextToken();
		}
		if (st.hasMoreTokens()) {
			r3 = st.nextToken();
		}
		
		try{
			
			if(cmp == 'I') {
				
				//logger.info("[HAServer] Received IWon message: " + mssg.toString());
				this.aelection.setTempLeader(r2);
				this.aelection.setTimeStamp(r3);
				return ack;
			
			} else if (cmp == 'L') {
				
				//logger.info("[HAServer] Received LEADER message: " + mssg.toString());
				
				// logger.debug("[HAServer] Get tempLeader: "+this.aelection.gettempLeader());
				
				if( this.aelection.gettempLeader().equals(r2)  && this.aelection.getTimeStamp().equals(r3) ) {
					return lead;
				} else {
					this.aelection.setTempLeader(none);
					this.aelection.setLeader(none);
					return no;
				}
				
			} else if (cmp == 'S') {
				
				//logger.info("[HAServer] Received SETLEAD message: " + mssg.toString());
				
				// logger.info("[HAServer] Get Leader: "+this.aelection.getLeader());
				
				if(! this.aelection.gettempLeader().equals(this.controllerID) ) {
					if ( this.aelection.gettempLeader().equals(r2) && this.aelection.getTimeStamp().equals(r3) ) {
						this.aelection.setLeader(r2);
						this.aelection.setTempLeader(none);
						return ack;
					} else {
						this.aelection.setTempLeader(none);
						this.aelection.setLeader(none);
						return no;
					}
				} else {
					this.aelection.setTempLeader(none);
					this.aelection.setLeader(none);
					return no;
				}
				
			} else if (cmp == 'Y'){
				
				//logger.info("[HAServer] Received YOU? message: " + mssg.toString());
				
				if( this.aelection.getLeader().equals(this.controllerID) ) {
					return this.controllerID+" "+r2;
				} else {
					return no;
				}
				
			} else if (cmp == 'H') {
				
				//logger.info("[HAServer] Received HEARTBEAT message: " + mssg.toString());
				
				if ( this.aelection.getLeader().equals(r2) ) {
					return ack+r3;
				} else {
					return no;
				}
				
			} else if (cmp == 'P') {
				
				// logger.info("[HAServer] Received PULSE message: " + mssg.toString());
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
				//TODO: Custom message template.
				return ack;
			}
		} catch (StringIndexOutOfBoundsException si) {
			logger.debug("[HAServer] Possible buffer overflow!");
			si.printStackTrace();
			return dc;
	    } catch (Exception e){
	    	logger.debug("[HAServer] Error while processing message!");
			e.printStackTrace();
			return dc;
		}
		
		return dc;
	}
	
}
