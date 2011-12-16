/**
*    Copyright 2011, Big Switch Networks, Inc. 
*    Originally created by David Erickson, Stanford University
* 
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

package net.floodlightcontroller.core.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IListener;

/**
 * Maintain lists of listeners ordered by dependency.  
 * 
 * @author readams
 *
 */
public class ListenerDispatcher<U, T extends IListener<U>> {
    protected static Logger logger = LoggerFactory.getLogger(ListenerDispatcher.class);
    List<T> listeners = null;
    
    /**
     * Add a listener to the list of listeners
     * @param listener
     */
    public void addListener(U type, T listener) {
        List<T> newlisteners = new ArrayList<T>();
        if (listeners != null)
            newlisteners.addAll(listeners);

        newlisteners.add(listener);
        
        // compute transitive closure of dependencies
        HashSet<String> deps = new HashSet<String>();
        for (T k : newlisteners) {
            for (T i : newlisteners) {
                for (T j : newlisteners) {
                    boolean ispre = 
                        (i.isCallbackOrderingPrereq(type, j.getName()) ||
                         j.isCallbackOrderingPostreq(type, i.getName()));
                    if (ispre || 
                            (deps.contains(i.getName() + "|||" + k.getName()) &&
                             deps.contains(k.getName() + "|||" + j.getName()))) {
                        deps.add(i.getName() + "|||" + j.getName());
                        // Check for dependency cycle
                        if (deps.contains(j.getName() + "|||" + i.getName())) {
                            logger.error("Cross dependency cycle between {} and {}",
                                      i.getName(), j.getName());
                        }
                    }
                }
            }
        }
        
        Collections.sort(newlisteners, new ListenerComparator(deps));
        listeners = newlisteners;
    }

    /**
     * Remove the given listener
     * @param listener the listener to remove
     */
    public void removeListener(T listener) {
        if (listeners != null) {
            List<T> newlisteners = new ArrayList<T>();
            newlisteners.addAll(listeners);
            newlisteners.remove(listener);
            listeners = newlisteners;
        }
    }
    
    /**
     * Clear all listeners
     */
    public void clearListeners() {
        listeners = new ArrayList<T>();
    }
    
    /** 
     * Get the ordered list of listeners ordered by dependencies 
     * @return
     */
    public List<T> getOrderedListeners() {
        return listeners;
    }
    
    /**
     * Comparator for listeners to use with computed transitive dependencies
     * 
     * @author readams
     *
     */
    protected class ListenerComparator implements Comparator<IListener<U>> {
        HashSet<String> deps;
        
        ListenerComparator(HashSet<String> deps) {
            this.deps = deps;
        }
        
        @Override
        public int compare(IListener<U> arg0, IListener<U> arg1) {
            if (deps.contains(arg0.getName() + "|||" + arg1.getName()))
                return 1;
            else if (deps.contains(arg1.getName() + "|||" + arg0.getName()))
                return -1;
            return 0;  // strictly partial ordering
        }
    }
}
