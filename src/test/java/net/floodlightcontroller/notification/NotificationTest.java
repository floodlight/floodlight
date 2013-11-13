package net.floodlightcontroller.notification;

import static org.junit.Assert.*;

import org.junit.Test;

public class NotificationTest {

    @Test
    public void testDynamicBinding() {
        System.setProperty(NotificationManagerFactory.NOTIFICATION_FACTORY_NAME,
                           "net.floodlightcontroller.notification.MockNotificationManagerFactory");
        NotificationManagerFactory.init();
        INotificationManagerFactory factory =
                NotificationManagerFactory.getNotificationManagerFactory();
        assertNotNull(factory);
        assertTrue(factory instanceof MockNotificationManagerFactory);
    }

}
