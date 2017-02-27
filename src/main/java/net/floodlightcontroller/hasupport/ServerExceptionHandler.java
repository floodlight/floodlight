package net.floodlightcontroller.hasupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

public class ServerExceptionHandler implements ChannelHandler {

	private static final Logger logger = LoggerFactory.getLogger(ServerExceptionHandler.class);

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable exp) throws Exception {
		try {
			logger.info(exp.getMessage());
		} catch (Exception e) {
			// e.printStackTrace();
		}
	}

	@Override
	public void handlerAdded(ChannelHandlerContext arg0) throws Exception {
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext arg0) throws Exception {
	}

}
