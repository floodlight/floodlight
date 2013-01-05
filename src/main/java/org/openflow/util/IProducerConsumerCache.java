package org.openflow.util;

/**
 * ProducerConsumer trigger interface
 * 
 * Start consumer cache:
 *   ProducerConsumer.getSingleton ().registerProducer (null, IProducerConsumerCache.class)
 * 
 * Deferred (late)
 *  producer consumer registration:
 *   ProducerConsumer.getSingleton ().registerProducer (IProducer, IProducerConsumerCache.class)
 *
 * End consumer cache:
 *   ProducerConsumer.getSingleton ().deregisterProducer (null, IProducerConsumerCache.class)
 * 
 */
public interface IProducerConsumerCache {

}
