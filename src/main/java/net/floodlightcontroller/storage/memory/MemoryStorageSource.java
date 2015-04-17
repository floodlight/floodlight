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

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.perfmon.IPktInProcessingTimeService;
import net.floodlightcontroller.storage.nosql.NoSqlStorageSource;
import net.floodlightcontroller.storage.SynchronousExecutorService;
import net.floodlightcontroller.storage.IStorageSourceService;
import net.floodlightcontroller.core.module.IFloodlightService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.storage.StorageException;

public class MemoryStorageSource extends NoSqlStorageSource {
    
    private Map<String, MemoryTable> tableMap = new HashMap<String,MemoryTable>();
    IPktInProcessingTimeService pktinProcessingTime;
    
    synchronized private MemoryTable getTable(String tableName, boolean create) {
        MemoryTable table = tableMap.get(tableName);
        if (table == null) {
            if (!create)
                throw new StorageException("Table " + tableName + " does not exist");
            table = new MemoryTable(tableName);
            tableMap.put(tableName, table);
        }
        return table;
    }
    
    @Override
    protected Collection<Map<String,Object>> getAllRows(String tableName, String[] columnNameList) {
        MemoryTable table = getTable(tableName, false);
        return table.getAllRows();
    }
    
    @Override
    protected Map<String,Object> getRow(String tableName, String[] columnNameList, Object rowKey) {
        MemoryTable table = getTable(tableName, false);
        return table.getRow(rowKey);
    }
    
    @Override
    protected List<Map<String,Object>> executeEqualityQuery(String tableName,
            String[] columnNameList, String predicateColumnName, Comparable<?> value) {
        MemoryTable table = getTable(tableName, false);
        List<Map<String,Object>> result = new ArrayList<Map<String,Object>>();
        synchronized (table) {
            Collection<Map<String,Object>> allRows = table.getAllRows();
            for (Map<String,Object> row : allRows) {
                Object v = row.get(predicateColumnName);
                if (value != null) {
                    if ((v != null) && value.equals(v))
                        result.add(row);
                } else if (v == null) {
                    result.add(row);
                }
            }
        }
        return result;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected List<Map<String,Object>> executeRangeQuery(String tableName,
            String[] columnNameList, String predicateColumnName,
            Comparable<?> startValue, boolean startInclusive, Comparable<?> endValue, boolean endInclusive) {
        MemoryTable table = getTable(tableName, false);
        List<Map<String,Object>> result = new ArrayList<Map<String,Object>>();
        synchronized (table) {
            Collection<Map<String,Object>> allRows = table.getAllRows();
            for (Map<String,Object> row : allRows) {
                Comparable value = (Comparable) row.get(predicateColumnName);
                if (value != null) {
                    int compareResult = value.compareTo(startValue);
                    if ((compareResult > 0) || (startInclusive && (compareResult >= 0))) {
                        compareResult = value.compareTo(endValue);
                        if ((compareResult < 0) || (startInclusive && (compareResult <= 0))) {
                            result.add(row);
                        }
                    }
                }
            }
        }
        return result;
    }
    
    @Override
    protected void insertRows(String tableName, List<Map<String,Object>> insertRowList) {
        MemoryTable table = getTable(tableName, false);
        String primaryKeyName = getTablePrimaryKeyName(tableName);
        synchronized (table) {
            for (Map<String,Object> row : insertRowList) {
                Object primaryKey = row.get(primaryKeyName);
                if (primaryKey == null) {
                    if (primaryKeyName.equals(DEFAULT_PRIMARY_KEY_NAME)) {
                        row = new HashMap<String,Object>(row);
                        primaryKey = table.getNextId();
                        row.put(primaryKeyName, primaryKey);
                    }
                }
                table.insertRow(primaryKey, row);
            }
        }
    }
    
    @Override
    protected void updateRows(String tableName, Set<Object> rowKeys, Map<String,Object> updateRowList) {
        MemoryTable table = getTable(tableName, false);
        synchronized (table) {
            for (Object rowKey : rowKeys) {
                Map<String,Object> row = table.getRow(rowKey);
                if (row == null)
                    row = table.newRow(rowKey);
                for (Map.Entry<String,Object> entry: updateRowList.entrySet()) {
                    row.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }
    
    @Override
    protected void updateRowsImpl(String tableName, List<Map<String,Object>> updateRowList) {
        MemoryTable table = getTable(tableName, false);
        String primaryKeyName = getTablePrimaryKeyName(tableName);
        synchronized (table) {
            for (Map<String,Object> updateRow : updateRowList) {
                Object rowKey = updateRow.get(primaryKeyName);
                if (rowKey == null)
                    throw new StorageException("Primary key not found.");
                Map<String,Object> row = table.getRow(rowKey);
                if (row == null)
                    row = table.newRow(rowKey);
                for (Map.Entry<String,Object> entry: updateRow.entrySet()) {
                    row.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }
    
    @Override
    protected void deleteRowsImpl(String tableName, Set<Object> rowKeys) {
        MemoryTable table = getTable(tableName, false);
        synchronized (table) {
            for (Object rowKey : rowKeys) {
                table.deleteRow(rowKey);
            }
        }
    }
    
    @Override
    public void createTable(String tableName, Set<String> indexedColumnNames) {
        super.createTable(tableName, indexedColumnNames);
        getTable(tableName, true);
    }
    
    public void setPktinProcessingTime(
            IPktInProcessingTimeService pktinProcessingTime) {
        this.pktinProcessingTime = pktinProcessingTime;
    }

    // IFloodlightModule methods

    @Override
    public void startUp(FloodlightModuleContext context) {
        super.startUp(context);
        executorService = new SynchronousExecutorService();
    }
    
    @Override
    public void init(FloodlightModuleContext context) throws net.floodlightcontroller.core.module.FloodlightModuleException {
    	super.init(context);
    };

    @Override
    public Map<Class<? extends IFloodlightService>,
               IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
            IFloodlightService> m =
                new HashMap<Class<? extends IFloodlightService>,
                            IFloodlightService>();
        m.put(IStorageSourceService.class, this);
        return m;
    }
}
