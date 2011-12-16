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

package net.floodlightcontroller.storage.memory;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class MemoryTable {

    private String tableName;
    private Map<Object,Map<String,Object>> rowMap;
    private int nextId;
    
    MemoryTable(String tableName) {
        this.tableName = tableName;
        rowMap = new TreeMap<Object,Map<String,Object>>();
        nextId = 0;
    }
    
    String getTableName() {
        return tableName;
    }
    
    Collection<Map<String,Object>> getAllRows() {
        return rowMap.values();
    }
    
    Map<String,Object> getRow(Object key) {
        Map<String,Object> row = rowMap.get(key);
        return row;
    }
    
    // rkv: Do we still need this? Probably needs to be tweaked a bit
    // to work with the support for specifying which column to use as the
    // primary key
    Map<String,Object> newRow(Object key) {
        Map<String,Object> row = new HashMap<String, Object>();
        row.put("id", key);
        rowMap.put(key, row);
        return row;
    }
    
    void insertRow(Object key, Map<String,Object> rowValues) {
        assert(key != null);
        rowMap.put(key, rowValues);
    }
    
    void deleteRow(Object rowKey) {
        rowMap.remove(rowKey);
    }
    
    Integer getNextId() {
        return new Integer(++nextId);
    }
}
