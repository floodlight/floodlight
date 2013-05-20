package net.floodlightcontroller.notification.syslog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.notification.INotificationManager;
import net.floodlightcontroller.notification.INotificationManagerFactory;

public class SyslogNotificationFactory implements
    INotificationManagerFactory {

    @Override
    public <T> INotificationManager getNotificationManager(Class<T> clazz) {
        Logger logger = LoggerFactory.getLogger(clazz.getCanonicalName() + ".syslog.notification");
        return new SyslogNotificationManager(logger);
    }

}
