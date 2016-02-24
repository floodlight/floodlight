package net.floodlightcontroller.notification;

public class MockNotificationManagerFactory implements
    INotificationManagerFactory {

    @Override
    public <T> INotificationManager getNotificationManager(Class<T> clazz) {
        return null;
    }

}
