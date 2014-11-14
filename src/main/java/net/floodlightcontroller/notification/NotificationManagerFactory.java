package net.floodlightcontroller.notification;

import net.floodlightcontroller.notification.syslog.SyslogNotificationFactory;

/**
 * This factory is a public utility to get NotificationManager
 * instance.
 *
 * @author kevinwang
 * @edited Ryan Izard, rizard@g.clemson.edu, ryan.izard@bigswich.com
 *
 */
public class NotificationManagerFactory {

    public static final String  NOTIFICATION_FACTORY_NAME =
            "floodlight.notification.factoryName";

    /**
     * Do not set the default here. Delay until init(), which will be
     * called by the JVM at class load. This will allow the unit tests
     * to test dynamic binding to a factory, then reset to the default
     * factory by clearing the System property and then calling init() 
     * again for subsequent unit tests that actually need a non-mocked 
     * NotificationManagerFactory.
     * 
     * If a dynamic binding is not specified, init() will fall through 
     * to else and the default of SyslogNotifcationFactory will be used.
     */
    private static INotificationManagerFactory factory; 

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
     * A simple mechanism to initialize factory with dynamic binding.
     * 
     * Extended to default to SyslogNotifcationFactory in the event
     * a dynamic binding is not specified via System properties.
     * This allows init() to be called multiple times for the unit tests
     * and select the default or a another factory if the System property
     * is cleared or is set, respectively.
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
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
         } else {
        	 factory = new SyslogNotificationFactory(); // use as the default
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
