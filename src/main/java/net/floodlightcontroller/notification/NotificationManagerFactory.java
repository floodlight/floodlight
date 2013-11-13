package net.floodlightcontroller.notification;

import net.floodlightcontroller.notification.syslog.SyslogNotificationFactory;

/**
 * This factory is a public untility to get NotificationManager
 * instance.
 *
 * @author kevinwang
 *
 */
public class NotificationManagerFactory {

    public static final String  NOTIFICATION_FACTORY_NAME =
            "floodlight.notification.factoryName";

    // default to SyslogNotificationFactory
    private static INotificationManagerFactory
        factory = new SyslogNotificationFactory();

    /**
     * Dynamically bind to a factory if there is one specified.
     * This provides a simple and very basic implementation to override
     * with a customized NotificationFactory.
     *
     */
    static {
        NotificationManagerFactory.init();
    }

    /**
     * A simple mechanism to initialize factory with dynamic binding
     */
    protected static void init() {
        String notificationfactoryClassName = null;
        try {
            notificationfactoryClassName =
                    System.getProperty(NOTIFICATION_FACTORY_NAME);
        } catch (SecurityException e) {
            throw new RuntimeException(e);
        }
        if (notificationfactoryClassName != null) {
            Class<?> nfc;
            try {
                nfc = Class.forName(notificationfactoryClassName);
                factory = (INotificationManagerFactory) nfc.newInstance();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } catch (InstantiationException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
         }
    }

    /**
     * Helper method to create a NotificationManager instance by class
     * with the underline factory
     * @param clazz
     * @return
     */
    public static <T> INotificationManager getNotificationManager(Class<T> clazz) {
        return factory.getNotificationManager(clazz);
    }

    /**
     * Helper method to return the factory
     * @return the INotificationManagerFactory instance
     */
    public static <T> INotificationManagerFactory getNotificationManagerFactory() {
        return factory;
    }

}
