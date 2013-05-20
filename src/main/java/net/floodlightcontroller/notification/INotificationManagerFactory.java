package net.floodlightcontroller.notification;

/**
 * This factory interface produce INotificationManager instance.
 *
 * @author kevin.wang@bigswitch.com
 *
 */
public interface INotificationManagerFactory {

    /**
     * Produce and returns a NotificationManager based on the name
     *
     * @param clazz
     * @return NotificationManager instance
     */
    <T> INotificationManager getNotificationManager(Class<T> clazz);
}
