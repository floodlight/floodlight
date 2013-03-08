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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import net.floodlightcontroller.core.annotations.LogMessageCategory;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.counter.ICounter;
import net.floodlightcontroller.counter.CounterStore;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.counter.CounterValue.CounterType;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.storage.web.StorageWebRoutable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@LogMessageCategory("System Database")
public abstract class AbstractStorageSource 
    implements IStorageSourceService, IFloodlightModule {
    protected static Logger logger = LoggerFactory.getLogger(AbstractStorageSource.class);

    // Shared instance of the executor to use to execute the storage tasks.
    // We make this a single threaded executor, because if we used a thread pool
    // then storage operations could be executed out of order which would cause
    // problems in some cases (e.g. delete and update of a row getting reordered).
    // If we wanted to make this more multi-threaded we could have multiple
    // worker threads/executors with affinity of operations on a given table
    // to a single worker thread. But for now, we'll keep it simple and just have
    // a single thread for all operations.
    protected static ExecutorService defaultExecutorService = Executors.newSingleThreadExecutor();

    protected final static String STORAGE_QUERY_COUNTER_NAME = "StorageQuery";
    protected final static String STORAGE_UPDATE_COUNTER_NAME = "StorageUpdate";
    protected final static String STORAGE_DELETE_COUNTER_NAME = "StorageDelete";
    
    protected Set<String> allTableNames = new CopyOnWriteArraySet<String>();
    protected ICounterStoreService counterStore;
    protected ExecutorService executorService = defaultExecutorService;
    protected IStorageExceptionHandler exceptionHandler;

    private Map<String, Set<IStorageSourceListener>> listeners =
        new ConcurrentHashMap<String, Set<IStorageSourceListener>>();

    // Our dependencies
    protected IRestApiService restApi = null;
    
    protected static final String DB_ERROR_EXPLANATION =
            "An unknown error occurred while executing asynchronous " +
            "database operation";
    
    @LogMessageDoc(level="ERROR",
            message="Failure in asynchronous call to executeQuery",
            explanation=DB_ERROR_EXPLANATION,
            recommendation=LogMessageDoc.GENERIC_ACTION)
    abstract class StorageCallable<V> implements Callable<V> {
        public V call() {
            try {
                return doStorageOperation();
            }
            catch (StorageException e) {
                logger.error("Failure in asynchronous call to executeQuery", e);
                if (exceptionHandler != null)
                    exceptionHandler.handleException(e);
                throw e;
            }
        }
        abstract protected V doStorageOperation();
    }
    
    @LogMessageDoc(level="ERROR",
            message="Failure in asynchronous call to updateRows",
            explanation=DB_ERROR_EXPLANATION,
            recommendation=LogMessageDoc.GENERIC_ACTION)
    abstract class StorageRunnable implements Runnable {
        public void run() {
            try {
                doStorageOperation();
            }
            catch (StorageException e) {
                logger.error("Failure in asynchronous call to updateRows", e);
                if (exceptionHandler != null)
                    exceptionHandler.handleException(e);
                throw e;
            }
        }
        abstract void doStorageOperation();
    }
    
    public AbstractStorageSource() {
        this.executorService = defaultExecutorService;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = (executorService != null) ?
                executorService : defaultExecutorService;
    }
    
    @Override
    public void setExceptionHandler(IStorageExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }
    
    @Override
    public abstract void setTablePrimaryKeyName(String tableName, String primaryKeyName);

    @Override
    public void createTable(String tableName, Set<String> indexedColumns) {
        allTableNames.add(tableName);
    }

    @Override
    public Set<String> getAllTableNames() {
        return allTableNames;
    }
    
    public void setCounterStore(CounterStore counterStore) {
        this.counterStore = counterStore;
    }
    
    protected void updateCounters(String baseName, String tableName) {
        if (counterStore != null) {
            String counterName;
            if (tableName != null) {
                updateCounters(baseName, null);
                counterName = baseName + CounterStore.TitleDelimitor + tableName;
            } else {
                counterName = baseName;
            }
            ICounter counter = counterStore.getCounter(counterName);
            if (counter == null) {
                counter = counterStore.createCounter(counterName, CounterType.LONG);
            }
            counter.increment();
        }
    }
    
    @Override
    public abstract IQuery createQuery(String tableName, String[] columnNames,
            IPredicate predicate, RowOrdering ordering);

    @Override
    public IResultSet executeQuery(IQuery query) {
        updateCounters(STORAGE_QUERY_COUNTER_NAME, query.getTableName());
        return executeQueryImpl(query);
    }
    
    protected abstract IResultSet executeQueryImpl(IQuery query);

    @Override
    public IResultSet executeQuery(String tableName, String[] columnNames,
            IPredicate predicate, RowOrdering ordering) {
        IQuery query = createQuery(tableName, columnNames, predicate, ordering);
        IResultSet resultSet = executeQuery(query);
        return resultSet;
    }

    @Override
    public Object[] executeQuery(String tableName, String[] columnNames,
            IPredicate predicate, RowOrdering ordering, IRowMapper rowMapper) {
        List<Object> objectList = new ArrayList<Object>();
        IResultSet resultSet = executeQuery(tableName, columnNames, predicate, ordering);
        while (resultSet.next()) {
            Object object = rowMapper.mapRow(resultSet);
            objectList.add(object);
        }
        return objectList.toArray();
    }
    
    @Override
    public Future<IResultSet> executeQueryAsync(final IQuery query) {
        Future<IResultSet> future = executorService.submit(
            new StorageCallable<IResultSet>() {
                public IResultSet doStorageOperation() {
                    return executeQuery(query);
                }
            });
        return future;
    }

    @Override
    public Future<IResultSet> executeQueryAsync(final String tableName,
            final String[] columnNames,  final IPredicate predicate,
            final RowOrdering ordering) {
        Future<IResultSet> future = executorService.submit(
            new StorageCallable<IResultSet>() {
                public IResultSet doStorageOperation() {
                    return executeQuery(tableName, columnNames,
                            predicate, ordering);
                }
            });
        return future;
    }

    @Override
    public Future<Object[]> executeQueryAsync(final String tableName,
            final String[] columnNames,  final IPredicate predicate,
            final RowOrdering ordering, final IRowMapper rowMapper) {
        Future<Object[]> future = executorService.submit(
            new StorageCallable<Object[]>() {
                public Object[] doStorageOperation() {
                    return executeQuery(tableName, columnNames, predicate,
                            ordering, rowMapper);
                }
            });
        return future;
    }

    @Override
    public Future<?> insertRowAsync(final String tableName,
            final Map<String,Object> values) {
        Future<?> future = executorService.submit(
            new StorageRunnable() {
                public void doStorageOperation() {
                    insertRow(tableName, values);
                }
            }, null);
        return future;
    }

    @Override
    public Future<?> updateRowsAsync(final String tableName, final List<Map<String,Object>> rows) {
        Future<?> future = executorService.submit(    
            new StorageRunnable() {
                public void doStorageOperation() {
                    updateRows(tableName, rows);
                }
            }, null);
        return future;
    }

    @Override
    public Future<?> updateMatchingRowsAsync(final String tableName,
            final IPredicate predicate, final Map<String,Object> values) {
        Future<?> future = executorService.submit(    
            new StorageRunnable() {
                public void doStorageOperation() {
                    updateMatchingRows(tableName, predicate, values);
                }
            }, null);
        return future;
    }

    @Override
    public Future<?> updateRowAsync(final String tableName,
            final Object rowKey, final Map<String,Object> values) {
        Future<?> future = executorService.submit(
            new StorageRunnable() {
                public void doStorageOperation() {
                    updateRow(tableName, rowKey, values);
                }
            }, null);
        return future;
    }

    @Override
    public Future<?> updateRowAsync(final String tableName,
            final Map<String,Object> values) {
        Future<?> future = executorService.submit(
            new StorageRunnable() {
                public void doStorageOperation() {
                    updateRow(tableName, values);
                }
            }, null);
        return future;
    }

    @Override
    public Future<?> deleteRowAsync(final String tableName, final Object rowKey) {
        Future<?> future = executorService.submit(
            new StorageRunnable() {
                public void doStorageOperation() {
                    deleteRow(tableName, rowKey);
                }
            }, null);
        return future;
    }

    @Override
    public Future<?> deleteRowsAsync(final String tableName, final Set<Object> rowKeys) {
        Future<?> future = executorService.submit(
                new StorageRunnable() {
                    public void doStorageOperation() {
                        deleteRows(tableName, rowKeys);
                    }
                }, null);
        return future;
    }

    @Override
    public Future<?> deleteMatchingRowsAsync(final String tableName, final IPredicate predicate) {
        Future<?> future = executorService.submit(
                new StorageRunnable() {
                    public void doStorageOperation() {
                        deleteMatchingRows(tableName, predicate);
                    }
                }, null);
        return future;
    }

    @Override
    public Future<?> getRowAsync(final String tableName, final Object rowKey) {
        Future<?> future = executorService.submit(
            new StorageRunnable() {
                public void doStorageOperation() {
                    getRow(tableName, rowKey);
                }
            }, null);
        return future;
    }
    
    @Override
    public Future<?> saveAsync(final IResultSet resultSet) {
        Future<?> future = executorService.submit(
            new StorageRunnable() {
                public void doStorageOperation() {
                    resultSet.save();
                }
            }, null);
        return future;
    }

    @Override
    public void insertRow(String tableName, Map<String, Object> values) {
        updateCounters(STORAGE_UPDATE_COUNTER_NAME, tableName);
        insertRowImpl(tableName, values);
    }

    protected abstract void insertRowImpl(String tableName, Map<String, Object> values);

    
    @Override
    public void updateRows(String tableName, List<Map<String,Object>> rows) {
        updateCounters(STORAGE_UPDATE_COUNTER_NAME, tableName);
        updateRowsImpl(tableName, rows);
    }

    protected abstract void updateRowsImpl(String tableName, List<Map<String,Object>> rows);

    @Override
    public void updateMatchingRows(String tableName, IPredicate predicate,
            Map<String, Object> values) {
        updateCounters(STORAGE_UPDATE_COUNTER_NAME, tableName);
        updateMatchingRowsImpl(tableName, predicate, values);
    }
    
    protected abstract void updateMatchingRowsImpl(String tableName, IPredicate predicate,
                                    Map<String, Object> values);

    @Override
    public void updateRow(String tableName, Object rowKey,
            Map<String, Object> values) {
        updateCounters(STORAGE_UPDATE_COUNTER_NAME, tableName);
        updateRowImpl(tableName, rowKey, values);
    }
    
    protected abstract void updateRowImpl(String tableName, Object rowKey,
                                   Map<String, Object> values);

    @Override
    public void updateRow(String tableName, Map<String, Object> values) {
        updateCounters(STORAGE_UPDATE_COUNTER_NAME, tableName);
        updateRowImpl(tableName, values);
    }
    
    protected abstract void updateRowImpl(String tableName, Map<String, Object> values);

    @Override
    public void deleteRow(String tableName, Object rowKey) {
        updateCounters(STORAGE_DELETE_COUNTER_NAME, tableName);
        deleteRowImpl(tableName, rowKey);
    }
    
    protected abstract void deleteRowImpl(String tableName, Object rowKey);

    @Override
    public void deleteRows(String tableName, Set<Object> rowKeys) {
        updateCounters(STORAGE_DELETE_COUNTER_NAME, tableName);
        deleteRowsImpl(tableName, rowKeys);
    }

    protected abstract void deleteRowsImpl(String tableName, Set<Object> rowKeys);

    @Override
    public void deleteMatchingRows(String tableName, IPredicate predicate) {
        IResultSet resultSet = null;
        try {
            resultSet = executeQuery(tableName, null, predicate, null);
            while (resultSet.next()) {
                resultSet.deleteRow();
            }
            resultSet.save();
        }
        finally {
            if (resultSet != null)
                resultSet.close();
        }
    }
    
    @Override
    public IResultSet getRow(String tableName, Object rowKey) {
        updateCounters(STORAGE_QUERY_COUNTER_NAME, tableName);
        return getRowImpl(tableName, rowKey);
    }

    protected abstract IResultSet getRowImpl(String tableName, Object rowKey);

    @Override
    public synchronized void addListener(String tableName, IStorageSourceListener listener) {
        Set<IStorageSourceListener> tableListeners = listeners.get(tableName);
        if (tableListeners == null) {
            tableListeners = new CopyOnWriteArraySet<IStorageSourceListener>();
            listeners.put(tableName, tableListeners);
        }
        tableListeners.add(listener);
    }
  
    @Override
    public synchronized void removeListener(String tableName, IStorageSourceListener listener) {
        Set<IStorageSourceListener> tableListeners = listeners.get(tableName);
        if (tableListeners != null) {
            tableListeners.remove(listener);
        }
    }

    @LogMessageDoc(level="ERROR",
            message="Exception caught handling storage notification",
            explanation="An unknown error occured while trying to notify" +
            		" storage listeners",
            recommendation=LogMessageDoc.GENERIC_ACTION)
    protected synchronized void notifyListeners(StorageSourceNotification notification) {
        if (logger.isTraceEnabled()) {
            logger.trace("Notifying storage listeneres: {}", notification);
        }
        String tableName = notification.getTableName();
        Set<Object> keys = notification.getKeys();
        Set<IStorageSourceListener> tableListeners = listeners.get(tableName);
        if (tableListeners != null) {
            for (IStorageSourceListener listener : tableListeners) {
                try {
                    switch (notification.getAction()) {
                        case MODIFY:
                            listener.rowsModified(tableName, keys);
                            break;
                        case DELETE:
                            listener.rowsDeleted(tableName, keys);
                            break;
                    }
                }
                catch (Exception e) {
                    logger.error("Exception caught handling storage notification", e);
                }
            }
        }
    }
    
    @Override
    public void notifyListeners(List<StorageSourceNotification> notifications) {
        for (StorageSourceNotification notification : notifications)
            notifyListeners(notification);
    }
    
    // IFloodlightModule

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IStorageSourceService.class);
        return l;
    }
    
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
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = 
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IRestApiService.class);
        l.add(ICounterStoreService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        restApi =
           context.getServiceImpl(IRestApiService.class);
        counterStore =
            context.getServiceImpl(ICounterStoreService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context) {
        restApi.addRestletRoutable(new StorageWebRoutable());
    }
}
