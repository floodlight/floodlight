package net.floodlightcontroller.core;

/**
 * This listener is a temporary solution to start flow reconciliation
 * after a Slave -> Master transition. It is fired once all known switches are
 * active (either because they all reconnected or because they did not
 * reconnect and have timed out).
 *
 * Modules should generally not rely on this notification unless there are
 * strong and compelling reasons to do so. I general modules should handle
 * the fact that some known switches are not active!
 * @author gregor
 *
 */
public interface IReadyForReconcileListener {
    public void readyForReconcile();
}
