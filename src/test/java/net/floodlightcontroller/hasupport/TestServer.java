package net.floodlightcontroller.hasupport;

import org.zeromq.ZMQ;

public class TestServer implements Runnable {
	
	String mockServerPort = new String();
	ZMQ.Context zmqcontext = ZMQ.context(1);
	ZMQ.Socket responder = zmqcontext.socket(ZMQ.REP);
	String reply = new String("ACK");
	
	public TestServer(String serverPort){
		this.mockServerPort = serverPort;
		responder.bind("tcp://"+mockServerPort);
		System.out.println("Starting client...");
	}

	public void run() {
		try{
			while (!Thread.currentThread().isInterrupted()) {
				byte[] response = responder.recv(0);
				System.out.println("Server Got: " + new String(response));
				responder.send(reply);
			}
			
		} catch (Exception e){
			e.printStackTrace();
			responder.close();
			zmqcontext.term();
			
		}
		responder.close();
		zmqcontext.term();
	}


}
