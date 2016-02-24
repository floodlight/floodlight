package net.floodlightcontroller.notification;

/**
 * Base interface for managing notifications.
 *
 * Notification is used to alert or inform notification receiver.
 * Notification can be a message written into log file or an SNMP trap or
 * SNMP notification.
 *
 * @author kevin.wang@bigswitch.com
 *
 */
public interface INotificationManager {

    /**
     * Post a notification. Depending on the underline implementation, it
     * may write the notes to log file or send an SNMP notification/trap.
     *
     * @param notes   string message to be sent to receiver
     */
    public void postNotification(String notes);

}
