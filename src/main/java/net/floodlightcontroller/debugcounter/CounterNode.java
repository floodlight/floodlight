package net.floodlightcontroller.debugcounter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

/**
 * A node in the counter hierarchy tree. We use a single tree and "merge" the
 * counter modules into this single tree.
 *
 * <li> Adding modules or counters to the tree must happen at the root of the tree.
 * <li> The root and the first level of the tree (root and module) are a shim
 * layers that con't have an actual counter value. We represent this with a
 * null counter.
 *
 * @author gregor
 */
class CounterNode implements Iterable<DebugCounterImpl> {
    private static final String QUOTED_SEP = Pattern.quote("/");
    private static final Logger log = LoggerFactory.getLogger(CounterNode.class);

    /** path/hierarchy of this counter without leading /. An empty string
     * represents the root. A string without a / is a module name
     */
    private final String hierarchy;
    /**
     * The path/hierarchy elements split into a list
     */
    private final List<String> hierarchyElements;
    /**
     * The actual counter instance for this node. Can be null for
     * root level and module level.
     */
    private final DebugCounterImpl counter;
    private final TreeMap<String, CounterNode> children = new TreeMap<>();

    /**
     * convert module name and counter hierarchy into list of
     * hierarchy elements
     * @param moduleName
     * @param counterHierarchy
     * @return
     */
    static List<String> getHierarchyElements(String moduleName, String counterHierarchy) {
        DebugCounterServiceImpl.verifyModuleNameSanity(moduleName);
        List<String> ret = new ArrayList<>();
        ret.add(moduleName);
        if (counterHierarchy == null || counterHierarchy.isEmpty()) {
            return ret;
        }
        for (String element : counterHierarchy.split(QUOTED_SEP)) {
            ret.add(element);
        }
        return ret;
    }

    private CounterNode(List<String> hierarchyElements, DebugCounterImpl counter) {
        super();
        this.hierarchyElements = ImmutableList.copyOf(hierarchyElements);
        this.hierarchy = Joiner.on("/").join(hierarchyElements);
        this.counter = counter;
    }

    /**
     * Create a new counter hierarchy tree and return the root
     * @return
     */
    public static CounterNode newTree() {
        return new CounterNode(ImmutableList.<String>of(), null);
    }

    /** verify that this node is the root */
    private void verifyIsRoot() {
        if (hierarchyElements.size() != 0) {
            throw new IllegalStateException("This is not the root. Can "
                    + "only call addCounter() on the root node. Current node: "
                    + hierarchy);
        }
    }

    /**
     *  return the full hierarchy as string, including module name
     */
    @Nonnull
    String getHierarchy() {
        return hierarchy;
    }

    /**
     * @return the list of hierarchy elements for this node
     */
    @Nonnull
    List<String> getHierarchyElements() {
        return hierarchyElements;
    }

    /**
     * @return this node's counters
     */
    @Nullable
    DebugCounterImpl getCounter() {
        return counter;
    }

    /**
     * Reset this counter all counter below it in the hierarchy
     */
    void resetHierarchy() {
        for (DebugCounterImpl cur: this) {
            cur.reset();
        }
    }

    /**
     * Return an Iterable over all DebugCounterImpls at and below this
     * node. Note we return an Iterable<DebugCounterImpls> not
     * Iterable<IDebugCounter> on purpose.
     * @return
     */
    Iterable<DebugCounterImpl> getCountersInHierarchy() {
        return this;
    }


    /**
     * Lookup the CounterNode identified by the hieraryElements if it exists.
     * Returns null if no such CounterNode is found. Must only be called on
     * the root of the tree.
     * @param hierarchyElements
     * @return
     */
    CounterNode lookup(List<String> hierarchyElements) {
        CounterNode cur = this;
        for (String element: hierarchyElements) {
            cur = cur.children.get(element);
            if (cur == null) {
                break;
            }
        }
        return cur;
    }
    
    /**
     * Remove the CounterNode identified by the hieraryElements if it exists.
     * Returns null if no such CounterNode is found. Must only be called on
     * the root of the tree.
     * @param hierarchyElements
     * @return
     */
    CounterNode remove(List<String> hierarchyElements) {
        CounterNode cur = this;
        /* The last element of the hierarchy is what we want to delete.
         * remove(len - 1) will shorten the hierarchy list to the parent of the last
         * descendant. This will be our stopping point. The parent of the last
         * descendant will contain a TreeMap of all it's "children." The String
         * key we removed can be used to remove the specific child from the parent's 
         * "children" TreeMap.
         * 
         * We're directly reusing the shrunken hierarchyElements List and
         * keyToRemove String, which IMHO is pretty cool how it works out.
         */
        
        /*
         * Make sure it's possible to remove something.
         */
        if (hierarchyElements.isEmpty()) {
        	log.error("Cannot remove a CounterNode from an empty list of hierarchy elements. Returning null.");
        	return null;
        } 
        String keyToRemove = hierarchyElements.remove(hierarchyElements.size() - 1); 
        for (String element: hierarchyElements) {
            cur = cur.children.get(element);
            if (cur == null) {
                break;
            }
        }
        // At this point, if !null, cur will be the parent of the CounterNode we want to remove.
        CounterNode removed = null;
        if (cur != null) {
        	removed = cur.children.remove(keyToRemove);
        }
        // TODO This will remove the CounterNode from IDebugCounterService, but if any
        // other modules still have a reference to the IDebugCounter within the CounterNode
        // we just removed, they might mistakenly query the "dead" counter.
        
        return removed;
    }

    /**
     * Add the given moduleName to the tree. Can only be called on the root.
     * If the module already exists, the all counters of the module will
     * be reset.
     * @param moduleName
     * @return true if the module was newly added, false if the module already
     * existed
     */
    boolean addModule(@Nonnull String moduleName) {
        verifyIsRoot();
        if (children.containsKey(moduleName)) {
            children.get(moduleName).resetHierarchy();
            return false;
        } else {
            CounterNode newNode =
                    new CounterNode(ImmutableList.of(moduleName), null);
            children.put(moduleName, newNode);
            return true;
        }
    }

    /**
     * Add the given Counter to the hierarchy. If the counterHierarcy already
     * exists, reset the hierarchy
     * @param counter
     * @return null if the counterHierarchy is newly registered, otherwise
     * returns the already registered DebugCounterImpl instance
     * @throws IllegalArgumentException if the parent of the counter does not
     * yet exist
     */
    @Nullable
    DebugCounterImpl addCounter(@Nonnull DebugCounterImpl counter) {
        verifyIsRoot();
        ArrayList<String> path = new ArrayList<>();
        path.add(counter.getModuleName());
        for (String element: counter.getCounterHierarchy().split(QUOTED_SEP)) {
            path.add(element);
        }
        String newCounterName = path.get(path.size()-1);

        CounterNode parent = lookup(path.subList(0, path.size()-1));
        if (parent == null) {
            throw new IllegalArgumentException("Missing hierarchy level for "
                    + "counter: " + counter.getModuleName() + " "
                    + counter.getCounterHierarchy());
        }
        if (parent.children.containsKey(newCounterName)) {
            CounterNode old = parent.children.get(newCounterName);
            // FIXME: we should check that old and new has the same
            // description and meta-data, otherwise we should probably thrown
            // and exception and refuse the operation.
            old.resetHierarchy();
            return old.counter;
        } else {
            CounterNode newNode = new CounterNode(path, counter);
            parent.children.put(newCounterName, newNode);
            return null;
        }
    }
    
    /**
     * Iterator over the counters in the counter hierarchy.
     * Iteration order is a pre-order tree walk. Children of a node are
     * visited in sorted order.
     * @author gregor
     */
    private final static class CounterIterator implements Iterator<DebugCounterImpl> {
        // NOTE: since some counters
        ArrayDeque<CounterNode> stack = new ArrayDeque<>();
        CounterNode curNode = null;
        private CounterIterator(CounterNode root) {
            stack.push(root);
            gotoNextNode();
        }

        private void gotoNextNode() {
            while (true) {
                curNode = null;
                if (stack.isEmpty()) {
                    break;
                }
                curNode = stack.pop();
                for (CounterNode child: curNode.children.descendingMap().values()) {
                    stack.push(child);
                }
                if (curNode.counter != null) {
                    break;
                }
            }
        }

        @Override
        public boolean hasNext() {
            return curNode != null;
        }

        @Override
        public DebugCounterImpl next() {
            if (curNode == null) {
                throw new NoSuchElementException();
            }
            DebugCounterImpl ret = curNode.counter;
            gotoNextNode();
            return ret;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public Iterator<DebugCounterImpl> iterator() {
        return new CounterIterator(this);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                 + ((children == null) ? 0 : children.hashCode());
        result = prime * result
                 + ((counter == null) ? 0 : counter.hashCode());
        result = prime * result
                 + ((hierarchy == null) ? 0 : hierarchy.hashCode());
        result = prime
                 * result
                 + ((hierarchyElements == null) ? 0
                                               : hierarchyElements.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        CounterNode other = (CounterNode) obj;
        if (children == null) {
            if (other.children != null) return false;
        } else if (!children.equals(other.children)) return false;
        if (counter == null) {
            if (other.counter != null) return false;
        } else if (!counter.equals(other.counter)) return false;
        if (hierarchy == null) {
            if (other.hierarchy != null) return false;
        } else if (!hierarchy.equals(other.hierarchy)) return false;
        if (hierarchyElements == null) {
            if (other.hierarchyElements != null) return false;
        } else if (!hierarchyElements.equals(other.hierarchyElements))
                                                                      return false;
        return true;
    }

    @Override
    public String toString() {
        return toString(0);
    }

    public String toString(int indent) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < indent; i++) {
            builder.append(" ");
        }
        builder.append("hierarchy=");
        builder.append(hierarchy);
        builder.append(", counter=");
        builder.append(counter);
        builder.append(", children=");
        builder.append(children.keySet());
        builder.append("\n");
        for (CounterNode child: children.values()) {
            builder.append(child.toString(indent + 3));
        }
        return builder.toString();
    }



}
