package net.floodlightcontroller.notification;

import junit.framework.Assert;

import org.junit.Test;

public class NotificationTest {

    @Test
    public void testDynamicBinding() {
        System.setProperty(NotificationManagerFactory.NOTIFICATION_FACTORY_NAME,
                           "net.floodlightcontroller.notification.MockNotificationManagerFactory");
        NotificationManagerFactory.init();
        INotificationManagerFactory factory =
                NotificationManagerFactory.getNotificationManagerFactory();
        Assert.assertNotNull(factory);
        Assert.assertTrue(factory instanceof MockNotificationManagerFactory);
    }

}
