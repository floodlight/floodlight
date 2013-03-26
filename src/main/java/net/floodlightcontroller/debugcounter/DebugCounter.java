package net.floodlightcontroller.debugcounter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * This class implements a central store for all counters used for debugging the
 * system. For counters based on traffic-type, see ICounterStoreService.
 *
 * @author Saurav
 */
public class DebugCounter implements IFloodlightModule, IDebugCounterService {
    protected static Logger log = LoggerFactory.getLogger(DebugCounter.class);

    /**
     * The counter value
     */
    protected class MutableLong {
        long value = 0;
        public void increment() { value += 1; }
        public long get() { return value; }
        public void set(long val) { value = val; }
      }

    /**
     * Global debug-counter storage across all threads. These are
     * updated from the local per thread counters by the update FIXME method.
     */
    protected ConcurrentHashMap<String, AtomicLong> debugCounters =
            new ConcurrentHashMap<String, AtomicLong>();

    /**
     * Thread local debug counters used for maintaining counters local to a thread.
     */
    protected final ThreadLocal<Map<String, MutableLong>> threadlocalCounters =
            new ThreadLocal<Map<String, MutableLong>>() {
        @Override
        protected Map<String, MutableLong> initialValue() {
            return new HashMap<String, MutableLong>();
        }
    };

    /**
     * protected class to store counter information
     */
    protected class CounterInfo {
        String moduleCounterName;
        String counterDesc;
        CounterType ctype;
        String moduleName;
        String counterName;

        public CounterInfo(String name, String desc, CounterType ctype) {
            this.moduleCounterName = name;
            String[] temp = name.split("-");
            this.moduleName = temp[0];
            this.counterName = temp[1];
            this.counterDesc = desc;
            this.ctype = ctype;
        }
    }

    /**
     * per module counters, indexed by the module name and storing Counter information.
     */
    protected ConcurrentHashMap<String, List<CounterInfo>> moduleCounters =
            new ConcurrentHashMap<String, List<CounterInfo>>();

    /**
     * fast cache for counter names that are currently active
     */
    Set<String> currentCounters = Collections.newSetFromMap(
                                      new ConcurrentHashMap<String,Boolean>());

   //*******************************
   //   IDebugCounterService
   //*******************************

   @Override
   public boolean registerCounter(String moduleCounterName, String counterDescription,
                               CounterType counterType) {
       if (debugCounters.containsKey(moduleCounterName)) {
           log.error("Cannot register counter: {}. Counter already exists",
                     moduleCounterName);
           return false;
       }
       String[] temp = moduleCounterName.split("-");
       if (temp.length < 2) {
           log.error("Cannot register counter: {}. Name not of type " +
                     " <module name>-<counter name>", moduleCounterName);
           return false;
       }

       // store counter information on a per module basis
       String moduleName = temp[0];
       List<CounterInfo> a;
       if (moduleCounters.containsKey(moduleName)) {
           a = moduleCounters.get(moduleName);
       } else {
           a = new ArrayList<CounterInfo>();
           moduleCounters.put(moduleName, a);
       }
       a.add(new CounterInfo(moduleCounterName, counterDescription, counterType));

       // create counter in global map
       debugCounters.put(moduleCounterName, new AtomicLong());

       // create counter in local thread map
       Map<String, MutableLong> thismap =  this.threadlocalCounters.get();
       MutableLong ml = thismap.get(moduleCounterName);
       if (ml == null) {
           thismap.put(moduleCounterName, new MutableLong());
       }

       // finally add to cache if it is meant to be always counted
       if (counterType == CounterType.ALWAYS_COUNT) {
           currentCounters.add(moduleCounterName);
       }
       return true;
   }

   @Override
   public void updateCounter(String moduleCounterName) {
       if (currentCounters.contains(moduleCounterName)) {
           Map<String, MutableLong> thismap =  this.threadlocalCounters.get();
           MutableLong ml = thismap.get(moduleCounterName);
           if (ml == null) {
               ml = new MutableLong();
               thismap.put(moduleCounterName, ml);
           }
           ml.increment();
       }
   }

   @Override
   public void flushCounters() {
       Map<String, MutableLong> thismap =  this.threadlocalCounters.get();
       for (String key : thismap.keySet()) {
           MutableLong curval = thismap.get(key);
           long delta = curval.get();
           if (delta > 0) {
               debugCounters.get(key).addAndGet(delta);
               curval.set(0);
           }
       }
   }

   @Override
   public void resetCounter(String moduleCounterName) {
       // TODO Auto-generated method stub

   }

   @Override
   public void resetAllCounters() {
       // TODO Auto-generated method stub

   }

   @Override
   public void resetAllModuleCounters(String moduleName) {
       // TODO Auto-generated method stub

   }

   @Override
   public void enableCtrOnDemand(String moduleCounterName) {
       // TODO Auto-generated method stub

   }

   @Override
   public void disableCtrOnDemand(String moduleCounterName) {
       // TODO Auto-generated method stub

   }

   @Override
   public DebugCounterInfo getCounterValue(String moduleCounterName) {
       // TODO Auto-generated method stub
       return null;
   }

   @Override
   public List<DebugCounterInfo> getAllCounterValues() {
       // TODO Auto-generated method stub
       return null;
   }

   @Override
   public List<DebugCounterInfo> getModuleCounterValues() {
       // TODO Auto-generated method stub
       return null;
   }


   //*******************************
   //   Internal Methods
   //*******************************

   private void printAllCounters() {
       for (List<CounterInfo> cilist : moduleCounters.values()) {
           for (CounterInfo ci : cilist) {
               log.info("Countername {} Countervalue {}", new Object[] {
                    ci.moduleCounterName, debugCounters.get(ci.moduleCounterName)
               });
           }
       }
   }


    //*******************************
    //   IFloodlightModule
    //*******************************

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IDebugCounterService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> m =
                new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(IDebugCounterService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        // TODO Auto-generated method stub

    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        // TODO Auto-generated method stub
    }

}
