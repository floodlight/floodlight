package net.floodlightcontroller.counter;

import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.counter.CounterStore.NetworkLayer;

public interface ICounterStoreService extends IFloodlightService {

    public final static String TitleDelimitor = "__";
    /** L2 EtherType subCategories */
    public final static String L3ET_IPV4 = "L3_IPv4";

    /**
     * Retrieve a list of subCategories by counterName.
     * null if nothing.
     */
    public List<String> getAllCategories(String counterName,
                                         NetworkLayer layer);

    /**
     * Create a new ICounter and set the title.  Note that the title must be 
     * unique, otherwise this will throw an IllegalArgumentException.
     * 
     * @param key
     * @param type
     * @return
     */
    public ICounter createCounter(String key, CounterValue.CounterType type);

    /**
     * Retrieves a counter with the given title, or null if none can be found.
     */
    public ICounter getCounter(String key);

    /**
     * Returns an immutable map of title:counter with all of the counters in the store.
     * 
     * (Note - this method may be slow - primarily for debugging/UI)
     */
    public Map<String, ICounter> getAll();
}
