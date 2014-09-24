package net.floodlightcontroller.debugcounter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;

import net.floodlightcontroller.core.IShutdownListener;
import net.floodlightcontroller.core.IShutdownService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebugCounterServiceImpl implements IFloodlightModule, IDebugCounterService {
    protected static final Logger logger =
            LoggerFactory.getLogger(DebugCounterServiceImpl.class);

    /**
     * The tree of counters.
     */
    private final CounterNode root = CounterNode.newTree();

    /**
     * protects the counter hierarchy tree. The writeLock is required to
     * change to hierarchy, i.e., adding nodes. The readLock is required
     * to query the counters or to reset them.
     */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    static void verifyStringSanity(String str, String name) {
        if (str == null) {
            if (name == null) {
                throw new NullPointerException();
            } else {
                throw new NullPointerException(name + " must not be null");
            }
        }
        if (str.isEmpty()) {
            if (name == null) {
                throw new IllegalArgumentException();
            } else {
                throw new IllegalArgumentException(name + " must not be empty");
            }
        }
    }

    static void verifyModuleNameSanity(String moduleName) {
        verifyStringSanity(moduleName, "moduleName");
        if (moduleName.contains("/")) {
            throw new IllegalArgumentException("moduleName must not contain /");
        }
    }

    @Override
    public boolean registerModule(String moduleName) {
        verifyModuleNameSanity(moduleName);
        lock.writeLock().lock();
        try {
            return root.addModule(moduleName);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public IDebugCounter registerCounter(@Nonnull String moduleName,
                                         @Nonnull String counterHierarchy,
                                         @Nonnull String counterDescription,
                                         @Nonnull MetaData... metaData) {
        verifyModuleNameSanity(moduleName);
        verifyStringSanity(counterHierarchy, "counterHierarchy");
        if (counterDescription == null) {
            try {
				throw new Exception("counterDescription must not be null");
			} catch (Exception e) {
				e.printStackTrace();
				logger.error(e.getMessage());
			}
        }
        if (metaData == null) {
            // somebody passing in a null array. sigh.
            throw new NullPointerException("metaData must not be null");
        }
        DebugCounterImpl counter =
                new DebugCounterImpl(moduleName, counterHierarchy,
                                     counterDescription,
                                     Arrays.asList(metaData));
        lock.writeLock().lock();
        try {
        	/* addCounter(counter) will return null if counter is accepted as a new counter
        	 * or it will return a reference to the existing DebugCounterImpl if the counter
        	 * is already present.
        	 */
            DebugCounterImpl oldCounter = root.addCounter(counter);
            if (oldCounter != null && logger.isDebugEnabled()) {
                logger.debug("Counter {} {} already registered. Resetting hierarchy",
                          moduleName, counterHierarchy);
            }
            /* If addCounter(counter) returned null, counter is the new counter, else if
             * addCounter(counter) returned a non-null reference, then the reference is 
             * the existing counter, which has just been reset and should be reused.
             */
            counter = (oldCounter == null ? counter : oldCounter);
        } finally {
            lock.writeLock().unlock();
        }
        return counter;
    }

    @GuardedBy("lock.readLock")
    private boolean resetInternal(List<String> hierarchyElements) {
        CounterNode node = root.lookup(hierarchyElements);
        if (node == null) {
            return false;
        }
        node.resetHierarchy();
        return true;
    }
    
    @GuardedBy("lock.readLock")
    private boolean removeInternal(List<String> hierarchyElements) {
        CounterNode node = root.lookup(hierarchyElements); // returns e.g. root/module-name/counter-node-to-remove
        if (node == null) {
            return false;
        }
        root.remove(hierarchyElements);
        return true;
    }

    @Override
    public boolean resetCounterHierarchy(String moduleName,
                                         String counterHierarchy) {
        verifyModuleNameSanity(moduleName);
        verifyStringSanity(counterHierarchy, "counterHierarchy");
        lock.readLock().lock();
        try {
            return resetInternal(CounterNode.getHierarchyElements(moduleName, counterHierarchy));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void resetAllCounters() {
        lock.readLock().lock();
        try {
            root.resetHierarchy();
        } finally {
            lock.readLock().unlock();
        }
    }


    @Override
    public boolean resetAllModuleCounters(String moduleName) {
        verifyModuleNameSanity(moduleName);
        lock.readLock().lock();
        try {
            return resetInternal(Collections.singletonList(moduleName));
        } finally {
            lock.readLock().unlock();
        }
    }
    
    @Override
    public boolean removeCounterHierarchy(String moduleName,
                                         String counterHierarchy) {
        verifyModuleNameSanity(moduleName);
        verifyStringSanity(counterHierarchy, "counterHierarchy");
        lock.readLock().lock();
        try {
            return removeInternal(CounterNode.getHierarchyElements(moduleName, counterHierarchy));
        } finally {
            lock.readLock().unlock();
        }
    }

    @GuardedBy("lock.readLock")
    private List<DebugCounterResource> getCountersFromNode(CounterNode node) {
        if (node == null) {
            return Collections.emptyList();
        }
        List<DebugCounterResource> ret = new ArrayList<>();
        for (DebugCounterImpl counter: node.getCountersInHierarchy()) {
            ret.add(new DebugCounterResource(counter));
        }
        return ret;
    }

    @Override
    public List<DebugCounterResource>
    getCounterHierarchy(String moduleName, String counterHierarchy) {
        verifyModuleNameSanity(moduleName);
        verifyStringSanity(counterHierarchy, "counterHierarchy");
        List<String> hierarchyElements =
                CounterNode.getHierarchyElements(moduleName, counterHierarchy);
        lock.readLock().lock();
        try {
            return getCountersFromNode(root.lookup(hierarchyElements));
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<DebugCounterResource> getAllCounterValues() {
        lock.readLock().lock();
        try {
            return getCountersFromNode(root);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<DebugCounterResource> getModuleCounterValues(String moduleName) {
        verifyModuleNameSanity(moduleName);
        List<String> hierarchyElements = Collections.singletonList(moduleName);
        lock.readLock().lock();
        try {
            return getCountersFromNode(root.lookup(hierarchyElements));
        } finally {
            lock.readLock().unlock();
        }
    }

    private class ShutdownListenenerDelegate implements IShutdownListener {
        @Override
        public void floodlightIsShuttingDown() {
            for (DebugCounterResource counter: getAllCounterValues()) {
                logger.info("Module {} counterHierarchy {} value " + counter.getCounterValue(),
                            counter.getModuleName(),
                            counter.getCounterHierarchy());
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
       deps.add(IShutdownService.class);
       return deps;
   }

   @Override
   public void init(FloodlightModuleContext context) {
   }

   @Override
   public void startUp(FloodlightModuleContext context) {
       IShutdownService shutdownService =
               context.getServiceImpl(IShutdownService.class);
       shutdownService.registerShutdownListener(new ShutdownListenenerDelegate());
   }

}
