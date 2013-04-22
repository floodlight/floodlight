package net.floodlightcontroller.debugcounter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
import net.floodlightcontroller.debugcounter.web.DebugCounterRoutable;
import net.floodlightcontroller.restserver.IRestApiService;

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
     * updated from the local per thread counters by the flush counters method.
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
    public static class CounterInfo {
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

        public String getModuleCounterName() { return moduleCounterName; }
        public String getCounterDesc() { return counterDesc; }
        public CounterType getCtype() { return ctype; }
        public String getModuleName() { return moduleName; }
        public String getCounterName() { return counterName; }
    }

    /**
     * per module counters, indexed by the module name and storing Counter information.
     */
    protected ConcurrentHashMap<String, List<CounterInfo>> moduleCounters =
            new ConcurrentHashMap<String, List<CounterInfo>>();

    /**
     * fast global cache for counter names that are currently active
     */
    Set<String> currentCounters = Collections.newSetFromMap(
                                      new ConcurrentHashMap<String,Boolean>());

    /**
     * Thread local cache for counter names that are currently active.
     */
    protected final ThreadLocal<Set<String>> threadlocalCurrentCounters =
            new ThreadLocal<Set<String>>() {
        @Override
        protected Set<String> initialValue() {
            return new HashSet<String>();
        }
    };

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
       // and add to counter name cache if it is meant to be always counted
       if (counterType != CounterType.COUNT_ON_DEMAND) {
           currentCounters.add(moduleCounterName);
           debugCounters.put(moduleCounterName, new AtomicLong());
       }
       return true;
   }

   @Override
   public void updateCounter(String moduleCounterName) {
       updateCounter(moduleCounterName, 1);
   }

   @Override
   public void updateCounter(String moduleCounterName, int incr) {
       Map<String, MutableLong> thismap =  this.threadlocalCounters.get();
       MutableLong ml = thismap.get(moduleCounterName);
       if (ml == null) {
           // check locally to see if this counter should be created or not
           // FIXME: this is an O(n) operation per update - change to a boolean check
           Set<String> thisset = this.threadlocalCurrentCounters.get();
           if (thisset.contains(moduleCounterName)) {
               ml = new MutableLong();
               ml.set(ml.get() + incr);
               thismap.put(moduleCounterName, ml);
           }
       } else {
           ml.increment();
       }
   }

   @Override
   public void flushCounters() {
       Map<String, MutableLong> thismap =  this.threadlocalCounters.get();
       ArrayList<String> deleteKeys = new ArrayList<String>();
       for (String key : thismap.keySet()) {
           MutableLong curval = thismap.get(key);
           long delta = curval.get();
           if (delta > 0) {
               AtomicLong ctr = debugCounters.get(key);
               if (ctr == null) {
                   // The global counter does not exist possibly because it has been
                   // disabled. It should thus be removed from the thread-local
                   // map (the counter) and set (the counter name). Removing it
                   // from the threadlocal set ensures that the counter will not be
                   // recreated (see updateCounter)
                   Set<String> thisset = this.threadlocalCurrentCounters.get();
                   thisset.remove(key);
                   deleteKeys.add(key);
               } else {
                   ctr.addAndGet(delta);
                   curval.set(0);
               }
           }
       }
       for (String dkey : deleteKeys)
           thismap.remove(dkey);

       // At this point it is also possible that the threadlocal map/set does not
       // include a counter that has been enabled and is present in the global
       // currentCounters set. If so we need to sync such state so that the
       // thread local counter can be created (in the updateCounter method)
       Set<String> thisset = this.threadlocalCurrentCounters.get();
       if (thisset.size() != currentCounters.size()) {
           thisset.addAll(currentCounters);
       }
   }

   @Override
   public void resetCounter(String moduleCounterName) {
       if (debugCounters.containsKey(moduleCounterName)) {
           debugCounters.get(moduleCounterName).set(0);
       }
   }

   @Override
   public void resetAllCounters() {
       for (AtomicLong v : debugCounters.values()) {
           v.set(0);
       }
   }

   @Override
   public void resetAllModuleCounters(String moduleName) {
       List<CounterInfo> cil = moduleCounters.get(moduleName);
       if (cil != null) {
           for (CounterInfo ci : cil) {
               if (debugCounters.containsKey(ci.moduleCounterName)) {
                   debugCounters.get(ci.moduleCounterName).set(0);
               }
           }
       } else {
           if (log.isDebugEnabled())
               log.debug("No module found with name {}", moduleName);
       }
   }

   @Override
   public void enableCtrOnDemand(String moduleCounterName) {
       currentCounters.add(moduleCounterName);
       debugCounters.putIfAbsent(moduleCounterName, new AtomicLong());
   }

   @Override
   public void disableCtrOnDemand(String moduleCounterName) {
       String[] temp = moduleCounterName.split("-");
       if (temp.length < 2) {
           log.error("moduleCounterName {} not recognized", moduleCounterName);
           return;
       }
       String moduleName = temp[0];
       List<CounterInfo> cil = moduleCounters.get(moduleName);
       for (CounterInfo ci : cil) {
           if (ci.moduleCounterName.equals(moduleCounterName) &&
               ci.ctype == CounterType.COUNT_ON_DEMAND) {
               currentCounters.remove(moduleCounterName);
               debugCounters.remove(moduleCounterName);
               return;
           }
       }
   }

   @Override
   public DebugCounterInfo getCounterValue(String moduleCounterName) {
       if (!debugCounters.containsKey(moduleCounterName)) return null;
       long counterValue = debugCounters.get(moduleCounterName).longValue();

       String[] temp = moduleCounterName.split("-");
       if (temp.length < 2) {
           log.error("moduleCounterName {} not recognized", moduleCounterName);
           return null;
       }
       String moduleName = temp[0];
       List<CounterInfo> cil = moduleCounters.get(moduleName);
       for (CounterInfo ci : cil) {
           if (ci.moduleCounterName.equals(moduleCounterName)) {
               DebugCounterInfo dci = new DebugCounterInfo();
               dci.counterInfo = ci;
               dci.counterValue = counterValue;
               return dci;
           }
       }
       return null;
   }

   @Override
   public List<DebugCounterInfo> getAllCounterValues() {
       List<DebugCounterInfo> dcilist = new ArrayList<DebugCounterInfo>();
       for (List<CounterInfo> cil : moduleCounters.values()) {
           for (CounterInfo ci : cil) {
               AtomicLong ctr = debugCounters.get(ci.moduleCounterName);
               if (ctr != null) {
                   DebugCounterInfo dci = new DebugCounterInfo();
                   dci.counterInfo = ci;
                   dci.counterValue = ctr.longValue();
                   dcilist.add(dci);
               }
           }
       }
       return dcilist;
   }

   @Override
   public List<DebugCounterInfo> getModuleCounterValues(String moduleName) {
       List<DebugCounterInfo> dcilist = new ArrayList<DebugCounterInfo>();
       if (moduleCounters.containsKey(moduleName)) {
           List<CounterInfo> cil = moduleCounters.get(moduleName);
           for (CounterInfo ci : cil) {
               AtomicLong ctr = debugCounters.get(ci.moduleCounterName);
               if (ctr != null) {
                   DebugCounterInfo dci = new DebugCounterInfo();
                   dci.counterInfo = ci;
                   dci.counterValue = ctr.longValue();
                   dcilist.add(dci);
               }
           }
       }
       return dcilist;
   }

   @Override
   public boolean containsMCName(String moduleCounterName) {
       if (debugCounters.containsKey(moduleCounterName)) return true;
       // it is possible that the counter may be disabled
       for (List<CounterInfo> cil : moduleCounters.values()) {
           for (CounterInfo ci : cil) {
               if (ci.moduleCounterName.equals(moduleCounterName))
                   return true;
           }
       }
       return false;
   }

   @Override
   public boolean containsModName(String moduleName) {
       return  (moduleCounters.containsKey(moduleName)) ? true : false;
   }

   //*******************************
   //   Internal Methods
   //*******************************

   protected void printAllCounters() {
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
       ArrayList<Class<? extends IFloodlightService>> deps =
               new ArrayList<Class<? extends IFloodlightService>>();
       deps.add(IRestApiService.class);
       return deps;
   }

   @Override
   public void init(FloodlightModuleContext context) throws FloodlightModuleException {

   }

   @Override
   public void startUp(FloodlightModuleContext context) {
       IRestApiService restService =
               context.getServiceImpl(IRestApiService.class);
       restService.addRestletRoutable(new DebugCounterRoutable());
   }

}
