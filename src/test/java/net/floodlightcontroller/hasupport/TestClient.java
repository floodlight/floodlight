package net.floodlightcontroller.hasupport;


import org.junit.Ignore;
import org.junit.Test;
import org.zeromq.ZMQ;

@Ignore 
public class TestClient {
	
	String mockClientPort = new String();
	ZMQ.Context zmqcontext = ZMQ.context(1);
	ZMQ.Socket requester = zmqcontext.socket(ZMQ.REQ);
	
	public TestClient(String clientPort){
		this.mockClientPort = clientPort;
		requester.connect("tcp://"+mockClientPort);
		System.out.println("Starting client...");
	}

	public String send(String message){
		try{ 
			requester.send(message.getBytes(),0);
			byte[] response = requester.recv();
			System.out.println("Server Response: " + new String(response));		
			return new String(response);
			
		} catch (Exception e){
			System.out.println(e);
			requester.close();
			zmqcontext.term();
			
		}
		return new String("none");
	}
	
	@Test
	public void nothing() {
		return;
	}


}
