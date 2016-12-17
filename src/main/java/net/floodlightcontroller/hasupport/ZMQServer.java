package net.floodlightcontroller.hasupport;

import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

/**
* The ZMQ Server
* This server class is instantiated by the ZMQNode (Connection Manager),
* and it connects to the back-end of the QueueDevice. This means that 
* more than one of these classes can be instantiated depending on the load.
* The server processes the request and sends out a reply, through the queue
* device to the front-end of the queue to which the clients connect to.
*
* @author Bhargav Srinivasan, Om Kale
*
*/

public class ZMQServer implements Runnable{
	
	
	private static Logger logger = LoggerFactory.getLogger(ZMQServer.class);
	private String serverPort = new String();
	
	private final AsyncElection aelection;
	private final String controllerID;
	
	private ZMQ.Context zmqcontext = ZMQ.context(1);
	
	/**
	 * Possible outgoing server messages, replies.
	 */
	
	private final String ack      = new String ("ACK");
	private final String no       = new String ("NO");
	private final String lead     = new String ("LEADOK");
	private final String dc       = new String ("DONTCARE");
	private final String none     = new String ("none");
	
	// Decide the socket timeout value based on how fast you think the leader should
	// respond and also how far apart the actual nodes are placed. If you are trying
	// to communicate with servers far away, then anything upto 10s would be a good value.
	
	public final Integer socketTimeout = new Integer(500);
	
	
	private String r1 = new String();
	private String r2 = new String();
	private String r3 = new String();
	private StringTokenizer st = new StringTokenizer(r1);
	
	/**
	 * 
	 * @param serverPort
	 */

	public ZMQServer(String serverPort, AsyncElection ae, String controllerID ) {
		// TODO Auto-generated constructor stub
		this.serverPort 		  = serverPort;
		this.aelection  		  = ae;
		this.controllerID		  = controllerID;
		
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		ZMQ.Socket serverSocket = zmqcontext.socket(ZMQ.REP);
		
		logger.info("Starting ZMQ Server on port: "+ this.serverPort.toString());
		
		serverSocket.connect("tcp://"+this.serverPort.toString());
	    serverSocket.setReceiveTimeOut(this.socketTimeout);
		serverSocket.setSendTimeOut(this.socketTimeout);
		
		byte[] rep;
		String stg;
		String reply;
		while(! Thread.currentThread().isInterrupted() ) {
			try{
				rep = serverSocket.recv(0);
				stg = new String(rep,0,rep.length);
				reply = processServerMessage(stg);
				serverSocket.send(reply.getBytes(),0);
			} catch (ZMQException ze){
				// ze.printStackTrace();
				//logger.info("[ZMQServer] ZMQ Exception in server (nothing received) !");
			} catch (Exception e){
				//e.printStackTrace();
				//logger.info("[ZMQServer] Exception in server!");
			}
		}
		
		return;
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
				
				//logger.info("[ZMQServer] Received IWon message: " + mssg.toString());
				this.aelection.setTempLeader(r2);
				this.aelection.setTimeStamp(r3);
				return ack;
			
			} else if (cmp == 'L') {
				
				//logger.info("[ZMQServer] Received LEADER message: " + mssg.toString());
				
				//logger.debug("[ZMQServer] Get tempLeader: "+this.aelection.gettempLeader()+" "+le);
				
				if( this.aelection.gettempLeader().equals(r2)  && this.aelection.getTimeStamp().equals(r3) ) {
					return lead;
				} else {
					this.aelection.setTempLeader(none);
					this.aelection.setLeader(none);
					return no;
				}
				
			} else if (cmp == 'S') {
				
				//logger.info("[ZMQServer] Received SETLEAD message: " + mssg.toString());
				
				//logger.info("[ZMQServer] Get Leader: "+this.aelection.getLeader()+" "+setl);
				
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
				
				//logger.info("[ZMQServer] Received YOU? message: " + mssg.toString());
				
				if( this.aelection.getLeader().equals(this.controllerID) ) {
					return this.controllerID+" "+r2;
				} else {
					return no;
				}
				
			} else if (cmp == 'H') {
				
				//logger.info("[ZMQServer] Received HEARTBEAT message: " + mssg.toString());
				
			
				if ( this.aelection.getLeader().equals(r2) ) {
					return ack+r3;
				} else {
					return no;
				}
				
			} else if (cmp == 'P') {
				
				//logger.debug("[ZMQServer] Received PULSE message: " + mssg.toString());
				return ack;
			
			} else if (cmp == 'B') {
				
				//logger.info("[ZMQServer] Received PUBLISH message");
				aelection.publishQueue();
				return ack;
				
			} else if (cmp == 'K') {
				
				//logger.info("[ZMQServer] Received SUBSCRIBE message");
				aelection.subscribeQueue(r2);
				return ack;
				
			} else if (cmp == 'm') {
				//TODO: Custom message template.
			}
		} catch (StringIndexOutOfBoundsException si) {
			si.printStackTrace();
			return dc;
	    } catch (Exception e){
			e.printStackTrace();
			return dc;
		}
		
		return dc;
	}

}
