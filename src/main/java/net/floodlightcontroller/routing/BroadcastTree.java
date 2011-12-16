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

package net.floodlightcontroller.routing;

import java.util.HashMap;

public class BroadcastTree {

    // Map of a node to the link in the tree
    protected HashMap<Long, Link> links_;
    // Map of node to its parent in the tree
    protected HashMap<Long, Long> nodes_;
    
    public BroadcastTree() {
        links_ = new HashMap<Long, Link>();
        nodes_ = new HashMap<Long, Long>();
    }
    
    public BroadcastTree(HashMap<Long, Link> links, HashMap<Long, Long> nodes) {
        setLinks(links, nodes);
    }
    
    public Link getTreeLink(long node) {
        return links_.get(node);
    }
    
    public HashMap<Long, Link> getLinks() {
        return links_;
    }
    
    public Long getParentNode(long node) {
        return nodes_.get(node);
    }
    
    public HashMap<Long, Long> getNodes() {
        return nodes_;
    }
    
    public void setLinks(HashMap<Long, Link> links, HashMap<Long, Long> nodes) {
        links_ = links;
        nodes_ = nodes;
    }
    
    public void addTreeLink(long parentNode, long myNode, Link link) {
        links_.put(myNode, link);
        nodes_.put(myNode, parentNode);
    }
}
