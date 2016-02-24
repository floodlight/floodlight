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

package net.floodlightcontroller.storage;

import java.util.Set;

public class StorageSourceNotification {
    
    public enum Action { MODIFY, DELETE };
    
    private String tableName;
    private Action action;
    private Set<Object> keys;
    
    public StorageSourceNotification() {
    }
    
    public StorageSourceNotification(String tableName, Action action, Set<Object> keys) {
        this.tableName = tableName;
        this.action = action;
        this.keys = keys;
    }
    
    public String getTableName() {
        return tableName;
    }
    
    public Action getAction() {
        return action;
    }
    
    public Set<Object> getKeys() {
        return keys;
    }
    
    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
    
    public void setAction(Action action) {
        this.action = action;
    }
    
    public void setKeys(Set<Object> keys) {
        this.keys = keys;
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 7867;
        int result = 1;
        result = prime * result + tableName.hashCode();
        result = prime * result + action.hashCode();
        result = prime * result + keys.hashCode();
        return result;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof StorageSourceNotification))
            return false;
        StorageSourceNotification other = (StorageSourceNotification) obj;
        if (tableName == null) {
            if (other.tableName != null)
                return false;
        } else if (!tableName.equals(other.tableName))
            return false;
        if (action == null) {
            if (other.action != null)
                return false;
        } else if (action != other.action)
            return false;
        if (keys == null) {
            if (other.keys != null)
                return false;
        } else if (!keys.equals(other.keys))
            return false;
        return true;
    }
    
    @Override
    public String toString() {
        return ("StorageNotification[table=" + tableName + "; action=" +
                 action.toString() + "; keys=" + keys.toString() + "]");
    }
}
