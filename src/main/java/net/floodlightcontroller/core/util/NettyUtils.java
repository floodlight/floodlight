package net.floodlightcontroller.core.util;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.Future;

/** Collection of static utility functions for netty.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class NettyUtils {
	private static final Logger logger = LoggerFactory.getLogger(NettyUtils.class);

	private static final long SHUTDOWN_TIMEOUT = 20;
	private static final TimeUnit SHUTDOWN_TIMEOUT_UNIT = TimeUnit.SECONDS;

	private NettyUtils() {}

	/**
	 * Shuts down an event group gracefully and waits for the corresponding Future to complete. Logs
	 * any exceptions.
	 *
	 * TODO: may want to force a shutdown if the graceful shutdown fails
	 *
	 * @param name - name of the even group to be shut down (for logging)
	 * @param group to be shut down
	 * @throws InterruptedException - if the thread is interrupted while shutting down
	 */
	public static void shutdownAndWait(String name, EventLoopGroup group) throws InterruptedException {
		try {
			logger.debug("Shutting down {}", name);
			Future<?> shutdownFuture = group.shutdownGracefully();
			shutdownFuture.get(SHUTDOWN_TIMEOUT, SHUTDOWN_TIMEOUT_UNIT);
			logger.debug("Done shutting down {}", name);
		} catch (ExecutionException e) {
			/***
			 * @id      NETTYUTIL4001
			 * @desc    A netty event loop group failed to shutdown
			 * @action  #support if encountered repeatedly
			 * @param   name the name of the event loop group
			 * @param   e the exception
			 */
			logger.warn("NETTYUTIL4001: Error during shutdown of {}: {}", name, e);
		} catch (TimeoutException e) {
			/***
			 * @id      NETTYUTIL4002
			 * @desc    A netty event loop group failed to shutdown
			 * @action  #support if encountered repeatedly
			 * @param   name the name of the event loop group
			 * @param   e the exception
			 */
			logger.warn("NETTYUTIL4002: Graceful shutdown of {} timed out: {}", name, e);
		}
	}

	/** Wait for the supplied set of futures to complete. Logs any exceptions occur.
	 *
	 * @param name name of futures to wait on for logging
	 * @param shutdownFutures futures to wait on
	 * @throws InterruptedException if process is interrupted
	 */
	public static void waitOrLog(String name, Future<?>... shutdownFutures)
			throws InterruptedException {
		logger.debug("Shutting down {}", name);
		long limit = System.nanoTime() + SHUTDOWN_TIMEOUT_UNIT.toNanos(SHUTDOWN_TIMEOUT);

		for(Future<?> f: shutdownFutures) {
			try {
				long wait = limit - System.nanoTime();
				if(wait > 0) {
					f.get(wait, TimeUnit.NANOSECONDS);
					logger.debug("Done shutting down {}", name);
				} else {
					throw new TimeoutException("timed out waiting for shutdown");
				}
			} catch (ExecutionException e) {
				/***
				 * @id      NETTYUTIL4003
				 * @desc    An error was encountered waiting for completion of future.
				 * @action  #support if encountered repeatedly
				 * @param   name the name of the future
				 * @param   e the exception
				 */
				logger.warn("Error during completion of {}: {}", name, e);
			} catch (TimeoutException e) {
				/***
				 * @id      NETTYUTIL4004
				 * @desc    A timeout was encountered waiting for completion of future.
				 * @action  #support if encountered repeatedly
				 * @param   name the name of the future
				 * @param   e the exception
				 */
				logger.warn("Graceful shutdown of {} timed out: {}", name, e);
				break;
			}

		}
	}
}