package org.openflow.util;

public interface IProducer {

    public void registerConsumer(Class<?> iface, Object anObj);

    public void deregisterConsumer(Class<?> iface, Object anObj);

}
