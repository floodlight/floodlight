package net.floodlightcontroller.hasupport;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

public class ServerChannelInitializer extends ChannelInitializer<SocketChannel> {
	private final AsyncElection aelection;
	private final String controllerID;

	public ServerChannelInitializer(AsyncElection aelection, String controllerID) {
		this.aelection = aelection;
		this.controllerID = controllerID;
	}

	@Override
	protected void initChannel(SocketChannel ch) throws Exception {
		ch.pipeline().addLast(new ServerExceptionHandler());
		ch.pipeline().addLast(new ServerChannelInboundHandler(aelection, controllerID));
	}
}
