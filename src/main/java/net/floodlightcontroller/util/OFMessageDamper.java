/**
 *    Copyright 2012, Big Switch Networks, Inc.
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

package net.floodlightcontroller.util;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

import net.floodlightcontroller.core.IOFSwitch;

import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dampens OFMessages sent to an OF switch. A message is only written to 
 * a switch if the same message (as defined by .equals()) has not been written
 * in the last n milliseconds. Timer granularity is based on TimedCache
 * @author gregor
 *
 */
public class OFMessageDamper {
    private static final Logger log = LoggerFactory.getLogger(OFMessageDamper.class);
    /**
     * An entry in the TimedCache. A cache entry consists of the sent message
     * as well as the switch to which the message was sent. 
     * 
     * NOTE: We currently use the full OFMessage object. To save space, we 
     * could use a cryptographic hash (e.g., SHA-1). However, this would 
     * obviously be more time-consuming.... 
     * 
     * We also store a reference to the actual IOFSwitch object and /not/
     * the switch DPID. This way we are guaranteed to not dampen messages if
     * a switch disconnects and then reconnects.
     * 
     * @author gregor
     */
    protected static class DamperEntry {
        OFMessage msg;
        IOFSwitch sw;
        public DamperEntry(OFMessage msg, IOFSwitch sw) {
            super();
            this.msg = msg;
            this.sw = sw;
        }
        /* (non-Javadoc)
         * @see java.lang.Object#hashCode()
         */
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((msg == null) ? 0 : msg.hashCodeIgnoreXid());
            result = prime * result + ((sw == null) ? 0 : sw.hashCode());
            return result;
        }
        /* (non-Javadoc)
         * @see java.lang.Object#equals(java.lang.Object)
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            DamperEntry other = (DamperEntry) obj;
            if (msg == null) {
                if (other.msg != null) return false;
            } else if (!msg.equalsIgnoreXid(other.msg)) return false;
            if (sw == null) {
                if (other.sw != null) return false;
            } else if (!sw.equals(other.sw)) return false;
            return true;
        }
        
      
    }
    
    TimedCache<DamperEntry> cache;
    EnumSet<OFType> msgTypesToCache;
    
    /**
     * 
     * @param capacity the maximum number of messages that should be 
     * kept
     * @param typesToDampen The set of OFMessageTypes that should be 
     * dampened by this instance. Other types will be passed through
     * @param timeout The dampening timeout. A message will only be
     * written if the last write for the an equal message more than
     * timeout ms ago. 
     */
    public OFMessageDamper(int capacity, 
                           Set<OFType> typesToDampen,  
                           int timeout) {
        cache = new TimedCache<DamperEntry>(capacity, timeout);
        msgTypesToCache = EnumSet.copyOf(typesToDampen);
    }        
    
    /**
     * write the message to the switch according to our dampening settings
     * @param sw
     * @param msg
     * @return true if the message was written to the switch, false if
     * the message was dampened. 
     */
    public boolean write(IOFSwitch sw, OFMessage msg) {
        if (!msgTypesToCache.contains(msg.getType())) {
            log.debug("Not dampening this type of msg {}", msg);
            sw.write(msg);
            return true;
        }
        
        DamperEntry entry = new DamperEntry(msg, sw);
        if (cache.update(entry)) {
            // entry exists in cache. Dampening.
            log.debug("Dampening cached msg {}", msg);
            return false; 
        } else {
            log.debug("Not dampening new msg {}", msg);
            sw.write(msg);
            return true;
        }
    }
    
    /**
     * Wrapper around {@link OFMessageDamper#write(IOFSwitch, OFMessage)}. 
     * @param sw
     * @param msgs
     * @return false if *any* message was dampened; true if no messages were dampened
     */
    public boolean write(IOFSwitch sw, Collection<OFMessage> msgs) {
        boolean allWritten = true;
        for (OFMessage msg : msgs) {
            if (!write(sw, msg)) {
                allWritten = false;
            }
        }
        return allWritten;
    }
}