package net.floodlightcontroller.core;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IShutdownService extends IFloodlightService {
    /**
     * Terminate floodlight process by calling System.exit() with the given
     * exitCode. If reason is non-null, reason will be logged. Before
     * terminating the shutdownListeners are called
     * If exitCode == 0 the termination cause is deemed "normal" and info
     * level log is used. In all other cases the exit is abnormal and an error
     * is logged.
     * @param reason
     * @param exitCode
     */
    public void terminate(@Nullable String reason, int exitCode);

    /**
     * Terminate floodlight process by calling System.exit() with the given
     * exitCode. If reason is non-null, reason will be logged. In addition,
     * the throwable will be logged as well.
     * Before terminating the shutdownListeners are called
     *
     * This method is generally used to terminate floodlight due to an
     * unhandled Exception. As such all messages are logged as error and it is
     * recommended that an exitCode != 0 is used.
     * @param reason
     * @param e The throwable causing floodlight to terminate
     * @param exitCode
     */
    public void terminate(@Nullable String reason,
                          @Nonnull Throwable e, int exitCode);

    /**
     * Register a shutdown listener. Registered listeners are called when
     * floodlight is about to terminate due to a call to terminate()
     * @param listener
     */
    public void registerShutdownListener(@Nonnull IShutdownListener listener);
}
