package net.floodlightcontroller.hasupport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;

public class NioClient {
	
	/**
	 * Doesn't hold socket objects, however, holds all
	 * general options, configs in order to create the sockets.
	 * 
	 * Now we are keeping all these general methods in this particular 
	 * object, why?, so that the underlying socket can be replaced
	 * without much hassle. The right thing was done when we made 
	 * Network Interface, so that we now don't have to touch anything in the 
	 * election algos. However we should have abstracted the socket from the 
	 * ConnectionManager, but I thought that would be too many layers
	 */
	
	private Integer sendTO;
	private Integer linger;
	private SocketChannel sc;
	private static final int READ_BUF_SIZE =  1024;
	
	/**
	 * Constructor should take all standard params required,
	 * like connection timeout, SO_LINGER etc.
	 */
	
	public NioClient(Integer sndTimeOut, Integer linger) {
		this.sendTO = sndTimeOut;
		this.linger = linger;
		
	}
	
	public SocketChannel connectClient(String host) {
		Integer port = Integer.valueOf(host.substring(10));
		String host2 = host.substring(0, 9);
		
		// System.out.println("HostPort:  "+ host2+":"+port);
		
		InetSocketAddress inet = new InetSocketAddress(host2,port);
		try {
			sc = SocketChannel.open(inet);
			sc.socket().setSoTimeout(sendTO);
			sc.socket().setTcpNoDelay(false);
			sc.socket().setSoLinger(false, linger);
			sc.socket().setReuseAddress(true);
			sc.socket().setPerformancePreferences(1, 2, 0);
			
			
			
			return sc;
		} catch (IOException e) {
			//e.printStackTrace();
			return null;
		}
	}
	
	public SocketChannel getSocketChannel() {
		try {
				return sc;
		} catch (Exception e) {
			//e.printStackTrace();
			return null;
		}
	}
	
	public Boolean send (String message) {
		if( message.equals(null) ) {
			return Boolean.FALSE;
		}
		
		try {
			sc.write( ByteBuffer.wrap(message.getBytes(Charset.forName("UTF-8"))) );
			return Boolean.TRUE;
		} catch (Exception e) {
			//e.printStackTrace();
			if(sc != null){
				this.deleteConnection();
			}
			return Boolean.FALSE;
		}

	}
	
	public String recv (){
		try {
			ByteBuffer dst = ByteBuffer.allocate(READ_BUF_SIZE);
			sc.read(dst);
			return new String(dst.array()).trim();
		} catch (Exception e) {
			//e.printStackTrace();
			if(sc != null){
				this.deleteConnection();
			}
			return "none";
		}
	}
	
	public Boolean deleteConnection () {
		try {
			if (sc != null) {
				sc.close();
				sc.socket().close();
			}
			return Boolean.TRUE;
		} catch (Exception e) {
			e.printStackTrace();
			return Boolean.FALSE;
		}
		
	}
	
//	public static void main(String[] args) {
//		NioClient nc = new NioClient(500,0);
//		nc.connectClient("127.0.0.1:4242");
//		//nc.connectClient("127.0.0.1", 9009);
//		nc.send("127.0.0.1:4242", "I'm a pedantic performer");
//		//nc.send("127.0.0.1:9009", "I'm a melancholic alcoholic");
//		System.out.println("This is what servers say these days: "+nc.recv("127.0.0.1:4242"));
//		//System.out.println("This is what servers say these days: "+nc.recv("127.0.0.1:9009"));
//		//nc.deleteConnection("127.0.0.1:9009");
//		
//		if ( !nc.send("127.0.0.1:9009", "I'm a melancholic alcoholic") ) {
//			System.out.println("Send was unsuccessful!");
//		}
//		
//		nc.send("127.0.0.1:4242", "I'm a coy comedian");
//		System.out.println("This is what servers say these days: "+nc.recv("127.0.0.1:4242"));
//		nc.deleteConnection("127.0.0.1:4242");
//	}

}
