package org.openflow.util;

import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.Set;

/**
 * The following implement a producer/consumer design pattern in which both
 * producers and consumers explicitly employ a centralized registration
 * mechanism, and java Interfaces are used as contracts.<br>
 */
public class ProducerConsumer {

    /*
     * Class variables
     */
    protected static ProducerConsumer singleton;

    /*
     * Default constructor
     */
    protected ProducerConsumer() {
        producerMap = new Hashtable<Class<?>, Set<IProducer>>();
    }

    /*
     * Instance variables
     */

    // Interface/IProducer map
    protected Map<Class<?>, Set<IProducer>> producerMap;

    /*
     * Protected methods
     */

    protected void _registerConsumer(Object consumer, Class<?>[] interfaces,
                                     Set<Class<?>> iSet,
                                     Set<Class<?>> iUniqueSet) {
        // *...Process all interfaces...*/
        for (Class<?> iface : interfaces) {

            // *...Protect against repeated interfaces...*/
            if (!iUniqueSet.contains(iface)) {
                iUniqueSet.add(iface);

                Set<IProducer> producers = producerMap.get(iface);

                if (producers != null) {
                    for (IProducer producer : producers)
                        producer.registerConsumer(iface, consumer);
                    iSet.add(iface);
                }

                // *...Recurse...*/
                _registerConsumer(consumer, iface.getInterfaces(), iSet,
                                  iUniqueSet);
            }
        }
    }

    protected void _registerConsumer(Object consumer, Class<?> clazz,
                                     Set<Class<?>> iSet,
                                     Set<Class<?>> iUniqueSet) {
        if (clazz != null) {
            // *...Process all interfaces...*/
            _registerConsumer(consumer, clazz.getInterfaces(), iSet,
                              iUniqueSet);

            // *...Recurse the class hierarchy...*/
            _registerConsumer(consumer, clazz.getSuperclass(), iSet,
                              iUniqueSet);
        }
    }

    protected int _deregisterConsumer(Object consumer,
                                      Class<?>[] interfaces,
                                      Set<Class<?>> iUniqueSet) {
        int count = 0;

        // *...Process all interfaces...*/
        for (Class<?> iface : interfaces) {

            // *...Protect against repeated interfaces...*/
            if (!iUniqueSet.contains(iface)) {
                iUniqueSet.add(iface);

                Set<IProducer> producers = producerMap.get(iface);

                if (producers != null) {
                    for (IProducer producer : producers)
                        producer.deregisterConsumer(iface, consumer);

                    count++;
                }

                // *...Recurse...*/
                count += _deregisterConsumer(consumer,
                                             iface.getInterfaces(),
                                             iUniqueSet);
            }
        }

        return count;
    }

    protected int _deregisterConsumer(Object consumer, Class<?> clazz,
                                      Set<Class<?>> iUniqueSet) {
        int count = 0;

        if (clazz != null) {
            // *...Process all interfaces...*/
            count += _deregisterConsumer(consumer, clazz.getInterfaces(),
                                         iUniqueSet);

            // *...Recurse the class hierarchy...*/
            count += _deregisterConsumer(consumer, clazz.getSuperclass(),
                                         iUniqueSet);
        }

        return count;
    }

    /*
     * Singleton API
     */

    /**
     * @return singleton ProducerConsumer
     */
    public static synchronized ProducerConsumer getSingleton() {
        if (singleton == null) singleton = new ProducerConsumer();

        return singleton;
    }

    /*
     * Producer APIs
     */

    /**
     * Producer registration
     * 
     * @param producer
     *            object that implements IProducer
     * @param iface
     *            interface supported by the producer
     * @return whether there was a previously registered producer, or true if
     *         one or more the arguments were invalid
     */
    public boolean registerProducer(IProducer producer, Class<?> iface) {
        if (producer != null && iface != null && iface.isInterface()) {
            Set<IProducer> producers = producerMap.get(iface);

            if (producers == null) {
                producers = new HashSet<IProducer>();
                producerMap.put(iface, producers);
            }

            return producers.add(producer);
        } else
            return true;
    }

    /**
     * Producer deregistration
     * 
     * @param producer
     *            object that implements IProducer
     * @param iface
     *            interface supported by the producer
     * @return whether the interface/producer pair was removed, or false if one
     *         or more the arguments were invalid
     */
    public boolean deregisterProducer(IProducer producer, Class<?> iface) {
        if (producer != null && iface != null && iface.isInterface()) {
            Set<IProducer> producers = producerMap.get(iface);

            if (producers != null) return producers.remove(producer);
        }

        return false;
    }

    /*
     * Consumer APIs
     */

    /**
     * Consumer registration
     * 
     * @param consumer
     *            object that implements producer-specific interfaces
     * @return set of supported interfaces
     */
    public Set<Class<?>> registerConsumer(Object consumer) {
        Set<Class<?>> iSet = new HashSet<Class<?>>();

        if (consumer != null)
                             _registerConsumer(consumer,
                                               consumer.getClass(), iSet,
                                               new HashSet<Class<?>>());

        return iSet;
    }

    /**
     * Consumer deregistration
     * 
     * @param consumer
     *            object to deregister
     * @return number of unregistered interfaces
     */
    public int deregisterConsumer(Object consumer) {
        if (consumer != null)
            return _deregisterConsumer(consumer, consumer.getClass(),
                                       new HashSet<Class<?>>());
        else
            return 0;
    }

}
