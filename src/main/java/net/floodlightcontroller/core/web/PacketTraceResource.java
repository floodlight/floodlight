/**
 *    Copyright 2013, Big Switch Networks, Inc.
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

package net.floodlightcontroller.core.web;

import java.util.concurrent.ConcurrentHashMap;

import org.restlet.data.Status;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.OFMessageFilterManager;

public class PacketTraceResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(PacketTraceResource.class);
    
    public static class FilterParameters {

        protected String sessionId = null;
        protected String mac = null;
        protected Integer period = null;
        protected String direction = null;
        protected String output = null;
        
        public String getSessionId() {
            return sessionId;
        }
        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }
        public String getMac() {
            return mac;
        }
        public void setMac(String mac) {
            this.mac = mac;
        }
        public Integer getPeriod() {
            return period;
        }
        public void setPeriod(Integer period) {
            this.period = period;
        }
        public String getDirection() {
            return direction;
        }
        public void setDirection(String direction) {
            this.direction = direction;
        }
        public String getOutput() {
            return output;
        }
        public void setOutput(String output) {
            this.output = output;
        }

        public String toString() {
            return "SessionID: " + sessionId +
                   "\tmac" + mac +
                   "\tperiod" + period +
                   "\tdirection" + direction +
                   "\toutput" + output;
        }
    }
    
    public static class PacketTraceOutput {
        protected String sessionId = null;

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }
    }
    
    @Post("json")
    public PacketTraceOutput packettrace(FilterParameters fp) {
        
        ConcurrentHashMap <String,String> filter = new ConcurrentHashMap<String,String> ();
        String sid = null;
        PacketTraceOutput output = new PacketTraceOutput();
        OFMessageFilterManager manager = 
                (OFMessageFilterManager)getContext()
                    .getAttributes().
                        get(OFMessageFilterManager.class.getCanonicalName());

        if (manager == null) {
            sid = null;
            setStatus(Status.SERVER_ERROR_SERVICE_UNAVAILABLE);
        }
        
        if (fp.getSessionId() != null) {
            filter.put("sessionId", fp.getSessionId());
        }
        if (fp.getMac() != null) {
            filter.put("mac", fp.getMac());
        }
        if (fp.getDirection() != null) {
            filter.put("direction", fp.getDirection());
        }
        
        if (filter.isEmpty()) {
            setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
        } else {
            if (log.isDebugEnabled()) {
                log.debug ("Call setupFilter: sid:{} filter:{}, period:{}", 
                           new Object[] {fp.getSessionId(), filter, 
                                         fp.getPeriod()*1000});
            }
            sid = manager.setupFilter(fp.getSessionId(), filter, 
                                      fp.getPeriod()*1000);
            output.setSessionId(sid);
            setStatus(Status.SUCCESS_OK);
        }
        
        return output;
    }
}
