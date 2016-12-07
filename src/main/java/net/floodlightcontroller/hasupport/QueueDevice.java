package net.floodlightcontroller.hasupport;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;

/**
 * The Queue Device
 * @author Bhargav Srinivasan, Om Kale
 */

public class QueueDevice implements Runnable{

	private static Logger logger = LoggerFactory.getLogger(QueueDevice.class);
	
	public final String serverPort;
	public final String clientPort;
	
	public QueueDevice(String servePort, String clienPort) {
		// TODO Auto-generated constructor stub
		// Chops "127.0.0.1:" to return the server and client port.
		this.serverPort = servePort.substring(10);
		this.clientPort = clienPort.substring(10);
	}
	
	
	@Override
	public void run() {
		
		try{
			/**
			 * Number of I/O threads assigned to the queue device.
			 */
			ZMQ.Context zmqcontext = ZMQ.context(1);
			
			/** 
			 * Connection facing the outside, where other nodes can connect 
			 * to this node. (frontend)
			 */

			ZMQ.Socket clientSide = zmqcontext.socket(ZMQ.ROUTER);
			clientSide.bind("tcp://0.0.0.0:"+this.clientPort.toString());
			
			
			/**
			 * The backend of the load balancing queue and the server 
			 * which handles all the incoming requests from the frontend.
			 * (backend)
			 */
			ZMQ.Socket serverSide = zmqcontext.socket(ZMQ.DEALER);
			serverSide.bind("tcp://0.0.0.0:"+this.serverPort.toString());
			
			logger.info("Starting ZMQ Queue Device on client side 0.0.0.0:"+this.clientPort+" server side 0.0.0.0:" +this.serverPort);
			
			/**
			 * This is an infinite loop to run the QueueDevice!
			 */
		//  Initialize poll set
	        ZMQ.Poller items = new ZMQ.Poller (2);
	        items.register(clientSide, ZMQ.Poller.POLLIN);
	        items.register(serverSide, ZMQ.Poller.POLLIN);

	        boolean more = false;
	        byte[] message;

	        //  Switch messages between sockets
	        while (!Thread.currentThread().isInterrupted()) {            
	            
	            items.poll(0);

	            if (items.pollin(0)) {
	                while (true) {
	                    // receive message
	                    message = clientSide.recv(0);
	                    more = clientSide.hasReceiveMore();

	                    // Broker it
	                    serverSide.send(message, more ? ZMQ.SNDMORE : 0);
	                    if(!more){
	                        break;
	                    }
	                }
	            }
	            if (items.pollin(1)) {
	                while (true) {
	                    // receive message
	                    message = serverSide.recv(0);
	                    more = serverSide.hasReceiveMore();
	                    // Broker it
	                    clientSide.send(message,  more ? ZMQ.SNDMORE : 0);
	                    if(!more){
	                        break;
	                    }
	                }
	            }
	            
	            TimeUnit.MICROSECONDS.sleep(30000);
	        }
	        //  We never get here but clean up anyhow
	        clientSide.close();
	        serverSide.close();
	        zmqcontext.term();
			
		} catch (ZMQException ze){		
			logger.debug("Zero MQ Exception occoured "+ze.toString());
			ze.printStackTrace();	
		} catch (Exception e){
			logger.debug("Exception occoured while trying to close QueueDevice "+e.toString());
			e.printStackTrace();
		}
		
	}

}
